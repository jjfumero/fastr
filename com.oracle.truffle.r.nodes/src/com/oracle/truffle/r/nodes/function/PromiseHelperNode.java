/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.function;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.InlineCacheNode;
import com.oracle.truffle.r.nodes.function.opt.ShareObjectNode;
import com.oracle.truffle.r.runtime.FastROptions;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.VirtualEvalFrame;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RPromise.EagerPromise;
import com.oracle.truffle.r.runtime.data.RPromise.EagerPromiseBase;
import com.oracle.truffle.r.runtime.data.RPromise.PromiseState;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Holds {@link RPromise}-related functionality that cannot be implemented in
 * "com.oracle.truffle.r.runtime.data" due to package import restrictions.
 */
public class PromiseHelperNode extends RBaseNode {

    public static class PromiseCheckHelperNode extends RBaseNode {

        @Child private PromiseHelperNode promiseHelper;

        private final ConditionProfile isPromiseProfile = ConditionProfile.createCountingProfile();

        /**
         * @return If obj is an {@link RPromise}, it is evaluated and its result returned
         */
        public Object checkEvaluate(VirtualFrame frame, Object obj) {
            if (isPromiseProfile.profile(obj instanceof RPromise)) {
                if (promiseHelper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    promiseHelper = insert(new PromiseHelperNode());
                }
                return promiseHelper.evaluate(frame, (RPromise) obj);
            }
            return obj;
        }
    }

    public static class PromiseDeoptimizeFrameNode extends RBaseNode {
        private final BranchProfile deoptimizeProfile = BranchProfile.create();

        /**
         * Guarantees, that all {@link RPromise}s in frame are deoptimized and thus are safe to
         * leave it's stack-branch.
         *
         * @param frame The frame to check for {@link RPromise}s to deoptimize
         * @return Whether there was at least on {@link RPromise} which needed to be deoptimized.
         */
        @TruffleBoundary
        public boolean deoptimizeFrame(MaterializedFrame frame) {
            boolean deoptOne = false;
            for (FrameSlot slot : frame.getFrameDescriptor().getSlots().toArray(new FrameSlot[0])) {
                // We're only interested in RPromises
                if (slot.getKind() != FrameSlotKind.Object) {
                    continue;
                }

                // Try to read it...
                try {
                    Object value = frame.getObject(slot);

                    // If it's a promise, deoptimize it!
                    if (value instanceof RPromise) {
                        RPromise promise = (RPromise) value;
                        if (!promise.isEvaluated()) {
                            deoptOne |= deoptimize(promise);
                        }
                    }
                } catch (FrameSlotTypeException err) {
                    // Should not happen after former check on FrameSlotKind!
                    throw RInternalError.shouldNotReachHere();
                }
            }
            return deoptOne;
        }

        private boolean deoptimize(RPromise promise) {
            if (!PromiseState.isDefaultOpt(promise.getState())) {
                deoptimizeProfile.enter();
                EagerPromiseBase eager = (EagerPromiseBase) promise;
                return eager.deoptimize();
            }

            // Nothing to do here; already the generic and slow RPromise
            return true;
        }
    }

    @Child private InlineCacheNode promiseClosureCache;

    @Child private PromiseHelperNode nextNode = null;

    @Children private final WrapArgumentNode[] wrapNodes = new WrapArgumentNode[ArgumentStatePush.MAX_COUNTED_ARGS];
    private final ConditionProfile shouldWrap = ConditionProfile.createBinaryProfile();

    @CompilationFinal private PrimitiveValueProfile optStateProfile = PrimitiveValueProfile.createEqualityProfile();
    private final ValueProfile isValidAssumptionProfile = ValueProfile.createIdentityProfile();
    private final ValueProfile promiseFrameProfile = ValueProfile.createClassProfile();

    /**
     * Main entry point for proper evaluation of the given Promise; including
     * {@link RPromise#isEvaluated()}, dependency cycles. Guarded by
     * {@link #isInOriginFrame(VirtualFrame,RPromise)}.
     *
     * @param frame The current {@link VirtualFrame}
     * @param promise The {@link RPromise} to evaluate
     * @return Evaluates the given {@link RPromise} in the given frame using the given inline cache
     */
    public Object evaluate(VirtualFrame frame, RPromise promise) {
        Object value = promise.getRawValue();
        if (isEvaluatedProfile.profile(value != null)) {
            return value;
        }

        Object obj;
        int state = optStateProfile.profile(promise.getState());
        if (PromiseState.isExplicit(state)) {
            CompilerDirectives.transferToInterpreter();
            // reset profiles, this is very likely a one-time event
            isEvaluatedProfile = ConditionProfile.createBinaryProfile();
            optStateProfile = PrimitiveValueProfile.createEqualityProfile();
            return evaluateSlowPath(frame, promise);
        }
        if (PromiseState.isDefaultOpt(state)) {
            obj = generateValueDefault(frame, promise);
        } else {
            obj = generateValueEager(frame, state, (EagerPromiseBase) promise);
        }
        if (isEvaluated(promise)) {
            // TODO: this only happens if compilation is in play and, as such, is difficult to track
            // why (there is no obvious call path...)
            return promise.getValue();
        } else {
            setValue(obj, promise);
            return obj;
        }
    }

    private Object generateValueDefault(VirtualFrame frame, RPromise promise) {
        // Check for dependency cycle
        if (isUnderEvaluation(promise)) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.PROMISE_CYCLE);
        }
        try {
            if (promiseClosureCache == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                promiseClosureCache = insert(InlineCacheNode.createPromise(FastROptions.PromiseCacheSize.getNonNegativeIntValue()));
            }
            promise.setUnderEvaluation();
            if (isInOriginFrame(frame, promise)) {
                return promiseClosureCache.execute(frame, promise.getClosure());
            } else {
                Frame promiseFrame = promiseFrameProfile.profile(promise.getFrame());
                assert promiseFrame != null;
                return promiseClosureCache.execute(wrapPromiseFrame(frame, promiseFrame), promise.getClosure());
            }
        } finally {
            promise.resetUnderEvaluation();
        }
    }

    private Object generateValueEager(VirtualFrame frame, int state, EagerPromiseBase promise) {
        assert !PromiseState.isDefaultOpt(state);
        if (!isDeoptimized(promise)) {
            Assumption eagerAssumption = isValidAssumptionProfile.profile(promise.getIsValidAssumption());
            if (eagerAssumption.isValid()) {
                if (!PromiseState.isEager(state)) {
                    RPromise nextPromise = (RPromise) promise.getEagerValue();
                    return checkNextNode().evaluate(frame, nextPromise);
                } else {
                    assert PromiseState.isEager(state);
                    return getEagerValue(frame, (EagerPromise) promise);
                }
            } else {
                CompilerDirectives.transferToInterpreter();
                promise.notifyFailure();

                // Fallback: eager evaluation failed, now take the slow path
                promise.materialize();
            }
        }
        // Call
        return generateValueDefault(frame, promise);
    }

    public static Object evaluateSlowPath(VirtualFrame frame, RPromise promise) {
        CompilerAsserts.neverPartOfCompilation();
        if (promise.isEvaluated()) {
            return promise.getValue();
        }

        int state = promise.getState();
        if (PromiseState.isExplicit(state)) {
            synchronized (promise) {
                if (promise.isEvaluated()) {
                    return promise.getValue();
                }
                Object obj = generateValueDefaultSlowPath(frame, promise);
                promise.setValue(obj);
                return obj;
            }
        }
        Object obj;
        if (PromiseState.isDefaultOpt(state)) {
            // Evaluate guarded by underEvaluation
            obj = generateValueDefaultSlowPath(frame, promise);
        } else {
            obj = generateValueEagerSlowPath(frame, state, (EagerPromiseBase) promise);
        }
        promise.setValue(obj);
        return obj;
    }

    private static Object generateValueDefaultSlowPath(VirtualFrame frame, RPromise promise) {
        // Check for dependency cycle
        if (promise.isUnderEvaluation()) {
            throw RError.error(RError.SHOW_CALLER, RError.Message.PROMISE_CYCLE);
        }
        try {
            promise.setUnderEvaluation();

            if (promise.isInOriginFrame(frame)) {
                return promise.getClosure().eval(frame.materialize());
            } else {
                Frame promiseFrame = promise.getFrame();
                assert promiseFrame != null;

                promiseFrame = wrapPromiseFrame(frame, promiseFrame);
                return promise.getClosure().eval(promiseFrame.materialize());
            }
        } finally {
            promise.resetUnderEvaluation();
        }
    }

    private static VirtualEvalFrame wrapPromiseFrame(VirtualFrame frame, Frame promiseFrame) {
        return VirtualEvalFrame.create(promiseFrame.materialize(), RArguments.getFunction(promiseFrame),
                        RCaller.createForPromise(RArguments.getCall(promiseFrame), frame));
    }

    private static Object generateValueEagerSlowPath(VirtualFrame frame, int state, EagerPromiseBase promise) {
        assert !PromiseState.isDefaultOpt(state);
        if (!promise.isDeoptimized()) {
            Assumption eagerAssumption = promise.getIsValidAssumption();
            if (eagerAssumption.isValid()) {
                if (!PromiseState.isEager(state)) {
                    RPromise nextPromise = (RPromise) promise.getEagerValue();
                    return evaluateSlowPath(frame, nextPromise);
                } else {
                    Object o = promise.getEagerValue();
                    if (promise.wrapIndex() != ArgumentStatePush.INVALID_INDEX) {
                        return ShareObjectNode.share(o);
                    }
                    return o;
                }
            } else {
                promise.notifyFailure();

                // Fallback: eager evaluation failed, now take the slow path
                promise.materialize();
            }
        }
        // Call
        return generateValueDefaultSlowPath(frame, promise);
    }

    /**
     * Materializes the promises' frame. After execution, it is guaranteed to be !=
     * <code>null</code>
     */
    public void materialize(RPromise promise) {
        if (isDefaultOptProfile.profile(!PromiseState.isDefaultOpt(promise.getState()))) {
            EagerPromiseBase eager = (EagerPromiseBase) promise;
            eager.materialize();
        }
        // otherwise: already the generic and slow RPromise
    }

    private PromiseHelperNode checkNextNode() {
        if (nextNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nextNode = insert(new PromiseHelperNode());
        }
        return nextNode;
    }

    private boolean isNullFrame(RPromise promise) {
        return isNullFrameProfile.profile(promise.isNullFrame());
    }

    private boolean isDeoptimized(EagerPromiseBase promise) {
        return isDeoptimizedProfile.profile(promise.isDeoptimized());
    }

    /**
     * @return Whether this promise represents a default (as opposed to supplied) argument.
     */
    public boolean isDefaultArgument(RPromise promise) {
        return isDefaultProfile.profile(promise.isDefaultArgument());
    }

    public boolean isEvaluated(RPromise promise) {
        return isEvaluatedProfile.profile(promise.isEvaluated());
    }

    @CompilationFinal private ConditionProfile isEvaluatedProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile underEvaluationProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isNullFrameProfile = ConditionProfile.createBinaryProfile();

    private final ConditionProfile isDefaultProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isFrameForEnvProfile = ConditionProfile.createBinaryProfile();

    private final ValueProfile valueProfile = ValueProfile.createClassProfile();

    // Eager
    private final ConditionProfile isDefaultOptProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile isDeoptimizedProfile = ConditionProfile.createBinaryProfile();
    private final ValueProfile eagerValueProfile = ValueProfile.createClassProfile();

    public PromiseHelperNode() {
    }

    /**
     * @return The state of the {@link RPromise#isUnderEvaluation()} flag.
     */
    public boolean isUnderEvaluation(RPromise promise) {
        return underEvaluationProfile.profile(promise.isUnderEvaluation());
    }

    /**
     * Used in case the {@link RPromise} is evaluated outside.
     *
     * @param newValue
     */
    public void setValue(Object newValue, RPromise promise) {
        promise.setValue(valueProfile.profile(newValue));
    }

    private static final int UNINITIALIZED = -1;
    private static final int GENERIC = -2;
    @CompilationFinal private int cachedWrapIndex = UNINITIALIZED;

    /**
     * Returns {@link EagerPromise#getEagerValue()} profiled.
     */
    @ExplodeLoop
    private Object getEagerValue(VirtualFrame frame, EagerPromise promise) {
        Object o = promise.getEagerValue();
        int wrapIndex = promise.wrapIndex();
        if (shouldWrap.profile(wrapIndex != ArgumentStatePush.INVALID_INDEX)) {
            if (cachedWrapIndex == UNINITIALIZED) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedWrapIndex = wrapIndex;
            }
            if (cachedWrapIndex != GENERIC && wrapIndex != cachedWrapIndex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedWrapIndex = GENERIC;
            }
            if (cachedWrapIndex != GENERIC) {
                if (cachedWrapIndex < ArgumentStatePush.MAX_COUNTED_ARGS) {
                    if (wrapNodes[cachedWrapIndex] == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        wrapNodes[cachedWrapIndex] = insert(WrapArgumentNode.create(cachedWrapIndex));
                    }
                    wrapNodes[cachedWrapIndex].execute(frame, o);
                }
            } else {
                for (int i = 0; i < ArgumentStatePush.MAX_COUNTED_ARGS; i++) {
                    if (wrapIndex == i) {
                        if (wrapNodes[i] == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            wrapNodes[i] = insert(WrapArgumentNode.create(i));
                        }
                        wrapNodes[i].execute(frame, o);
                    }
                }
            }
        }
        return eagerValueProfile.profile(o);
    }

    /**
     * @param frame
     * @return Whether the given {@link RPromise} is in its origin context and thus can be resolved
     *         directly inside the AST.
     */
    public boolean isInOriginFrame(VirtualFrame frame, RPromise promise) {
        if (isDefaultArgument(promise) && isNullFrame(promise)) {
            return true;
        }
        if (frame == null) {
            return false;
        }
        return isFrameForEnvProfile.profile(frame == promise.getFrame());
    }
}
