/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.access.variables.LocalReadVariableNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.FastROptions;
import com.oracle.truffle.r.runtime.RDeparse;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.builtins.RBuiltinDescriptor;
import com.oracle.truffle.r.runtime.builtins.RSpecialFactory;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxCall;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

final class PeekLocalVariableNode extends RNode implements RSyntaxLookup {

    @Child private LocalReadVariableNode read;

    private final ConditionProfile isPromiseProfile = ConditionProfile.createBinaryProfile();
    private final ValueProfile valueProfile = ValueProfile.createClassProfile();

    PeekLocalVariableNode(String name) {
        this.read = LocalReadVariableNode.create(name, false);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object value = read.execute(frame);
        if (value == null) {
            throw RSpecialFactory.throwFullCallNeeded();
        }
        if (isPromiseProfile.profile(value instanceof RPromise)) {
            RPromise promise = (RPromise) value;
            if (!promise.isEvaluated()) {
                throw RSpecialFactory.throwFullCallNeeded();
            }
            return valueProfile.profile(promise.getValue());
        }
        return valueProfile.profile(value);
    }

    @Override
    public void setSourceSection(SourceSection source) {
        // nothing to do
    }

    @Override
    public String getIdentifier() {
        return (String) read.getIdentifier();
    }

    @Override
    public boolean isFunctionLookup() {
        return false;
    }

    @Override
    public SourceSection getLazySourceSection() {
        return null;
    }
}

public final class RCallSpecialNode extends RCallBaseNode implements RSyntaxNode, RSyntaxCall {

    private static final boolean useSpecials = FastROptions.UseSpecials.getBooleanValue();

    // currently cannot be RSourceSectionNode because of TruffleDSL restrictions
    @CompilationFinal private SourceSection sourceSection;

    @Override
    public void setSourceSection(SourceSection sourceSection) {
        assert sourceSection != null;
        this.sourceSection = sourceSection;
    }

    @Override
    public SourceSection getLazySourceSection() {
        return sourceSection;
    }

    @Override
    public SourceSection getSourceSection() {
        RDeparse.ensureSourceSection(this);
        return sourceSection;
    }

    @Child private RNode functionNode;
    @Child private RNode special;

    private final RSyntaxNode[] arguments;
    private final ArgumentsSignature signature;
    private final RFunction expectedFunction;

    /**
     * If this is true, then any bailout should simply be forwarded by re-throwing the exception.
     */
    private boolean propagateFullCallNeededException;

    /**
     * If this is non-null, then any bailout should lead to be forwarded by re-throwing the
     * exception after replacing itself with a proper call node.
     */
    private RCallSpecialNode callSpecialParent;

    private RCallSpecialNode(SourceSection sourceSection, RNode functionNode, RFunction expectedFunction, RSyntaxNode[] arguments, ArgumentsSignature signature, RNode special) {
        this.sourceSection = sourceSection;
        this.expectedFunction = expectedFunction;
        this.special = special;
        this.functionNode = functionNode;
        this.arguments = arguments;
        this.signature = signature;
    }

    /**
     * This passes {@code true} for the isReplacement parameter and ignores the specified arguments,
     * i.e., does not modify them in any way before passing it to
     * {@link RSpecialFactory#create(ArgumentsSignature, RNode[], boolean)}.
     */
    public static RSyntaxNode createCallInReplace(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments, int... ignoredArguments) {
        return createCall(sourceSection, functionNode, signature, arguments, true, ignoredArguments);
    }

    public static RSyntaxNode createCall(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments) {
        return createCall(sourceSection, functionNode, signature, arguments, false);
    }

    private static RSyntaxNode createCall(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments, boolean inReplace, int... ignoredArguments) {
        RCallSpecialNode special = null;
        if (useSpecials) {
            special = tryCreate(sourceSection, functionNode, signature, arguments, inReplace, ignoredArguments);
        }
        if (special != null) {
            return special;
        } else {
            return RCallNode.createCall(sourceSection, functionNode, signature, arguments);
        }
    }

    private static RCallSpecialNode tryCreate(SourceSection sourceSection, RNode functionNode, ArgumentsSignature signature, RSyntaxNode[] arguments, boolean inReplace, int[] ignoredArguments) {
        RSyntaxNode syntaxFunction = functionNode.asRSyntaxNode();
        if (!(syntaxFunction instanceof RSyntaxLookup)) {
            // LHS is not a simple lookup -> bail out
            return null;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (contains(ignoredArguments, i)) {
                continue;
            }
            if (!(arguments[i] instanceof RSyntaxLookup || arguments[i] instanceof RSyntaxConstant || arguments[i] instanceof RCallSpecialNode)) {
                // argument is not a simple lookup or constant value or another special -> bail out
                return null;
            }
        }
        String name = ((RSyntaxLookup) syntaxFunction).getIdentifier();
        RBuiltinDescriptor builtinDescriptor = RContext.lookupBuiltinDescriptor(name);
        if (builtinDescriptor == null) {
            // no builtint -> bail out
            return null;
        }
        RSpecialFactory specialCall = builtinDescriptor.getSpecialCall();
        if (specialCall == null) {
            // no special call definition -> bail out
            return null;
        }
        RNode[] localArguments = new RNode[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            RSyntaxNode arg = arguments[i];
            if (inReplace && contains(ignoredArguments, i)) {
                localArguments[i] = arg.asRNode();
            } else {
                if (arg instanceof RSyntaxLookup) {
                    localArguments[i] = new PeekLocalVariableNode(((RSyntaxLookup) arg).getIdentifier());
                } else if (arg instanceof RSyntaxConstant) {
                    localArguments[i] = RContext.getASTBuilder().process(arg).asRNode();
                } else {
                    assert arg instanceof RCallSpecialNode;
                    localArguments[i] = arg.asRNode();
                }
            }
        }
        RNode special = specialCall.create(signature, localArguments, inReplace);
        if (special == null) {
            // the factory refused to create a special call -> bail out
            return null;
        }
        RFunction expectedFunction = RContext.lookupBuiltin(name);
        RInternalError.guarantee(expectedFunction != null);

        RCallSpecialNode callSpecial = new RCallSpecialNode(sourceSection, functionNode, expectedFunction, arguments, signature, special);
        for (int i = 0; i < arguments.length; i++) {
            if (!inReplace || !contains(ignoredArguments, i)) {
                if (arguments[i] instanceof RCallSpecialNode) {
                    ((RCallSpecialNode) arguments[i]).setCallSpecialParent(callSpecial);
                }
            }
        }
        return callSpecial;
    }

    private static boolean contains(int[] ignoredArguments, int index) {
        for (int i = 0; i < ignoredArguments.length; i++) {
            if (ignoredArguments[i] == index) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object execute(VirtualFrame frame, Object function) {
        try {
            if (function != expectedFunction) {
                // the actual function differs from the expected function
                throw RSpecialFactory.throwFullCallNeeded();
            }
            return special.execute(frame);
        } catch (RSpecialFactory.FullCallNeededException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (propagateFullCallNeededException) {
                throw e;
            }
            RCallNode callNode = getRCallNode();
            for (RSyntaxElement arg : arguments) {
                if (arg instanceof RCallSpecialNode) {
                    ((RCallSpecialNode) arg).setCallSpecialParent(null);
                }
            }
            if (callSpecialParent != null) {
                RSyntaxNode[] args = callSpecialParent.arguments;
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == this) {
                        args[i] = callNode;
                    }
                }
                throw e;
            }
            return replace(callNode).execute(frame, function);
        }
    }

    private RCallNode getRCallNode(RSyntaxNode[] newArguments) {
        return RCallNode.createCall(sourceSection, functionNode, signature, newArguments);
    }

    private RCallNode getRCallNode() {
        return getRCallNode(arguments);
    }

    /**
     * see {@link #propagateFullCallNeededException}.
     */
    public void setPropagateFullCallNeededException() {
        propagateFullCallNeededException = true;
    }

    /**
     * see {@link #callSpecialParent}.
     */
    private void setCallSpecialParent(RCallSpecialNode call) {
        callSpecialParent = call;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return execute(frame, functionNode.execute(frame));
    }

    @Override
    public RSyntaxElement getSyntaxLHS() {
        return functionNode == null ? RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "FUN", true) : functionNode.asRSyntaxNode();
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return signature == null ? ArgumentsSignature.empty(1) : signature;
    }

    @Override
    public RSyntaxElement[] getSyntaxArguments() {
        return arguments == null ? new RSyntaxElement[]{RSyntaxLookup.createDummyLookup(RSyntaxNode.LAZY_DEPARSE, "...", false)} : arguments;
    }
}
