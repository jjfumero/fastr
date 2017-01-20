/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.engine.interop.ffi;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.engine.interop.ffi.TruffleCallFactory.InvokeTruffleNodeGen;
import com.oracle.truffle.r.engine.interop.ffi.TruffleCallFactory.SplitTruffleCallRFFINodeGen;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.ContextState;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.ffi.CallRFFI;
import com.oracle.truffle.r.runtime.ffi.DLL.SymbolHandle;
import com.oracle.truffle.r.runtime.ffi.NativeCallInfo;
import com.oracle.truffle.r.runtime.ffi.RFFIVariables;
import com.oracle.truffle.r.runtime.ffi.jni.JNI_Call;
import com.oracle.truffle.r.runtime.ffi.jni.JNI_Call.JNI_CallRFFINode;
import com.oracle.truffle.r.runtime.ffi.truffle.TruffleRFFIFrameHelper;

class TruffleCall implements CallRFFI {
    private static TruffleCall truffleCall;
    private static TruffleObject truffleCallTruffleObject;
    private static TruffleObject truffleCallHelper;

    @SuppressWarnings("unused")
    TruffleCall() {
        new JNI_Call();
        truffleCall = this;
        truffleCallTruffleObject = JavaInterop.asTruffleObject(truffleCall);
        TrufflePkgInit.initialize();
        truffleCallHelper = TruffleCallHelper.initialize();
    }

    static class ContextStateImpl implements RContext.ContextState {
        private RContext context;
        private boolean initVariablesDone;

        @Override
        public ContextState initialize(RContext contextA) {
            this.context = contextA;
            context.addExportedSymbol("_fastr_rffi_call", truffleCallTruffleObject);
            context.addExportedSymbol("_fastr_rffi_callhelper", truffleCallHelper);
            return this;
        }

        @Override
        public void beforeDestroy(RContext contextA) {
        }
    }

    static ContextStateImpl newContextState() {
        return new ContextStateImpl();
    }

    private enum INIT_VAR_FUN {
        OBJ,
        DOUBLE,
        INT;

        private final String funName;
        private SymbolHandle symbolHandle;

        INIT_VAR_FUN() {
            funName = "Call_initvar_" + name().toLowerCase();
        }
    }

    private static void initVariables(RContext context) {
        // must have parsed the variables module in libR
        for (INIT_VAR_FUN initVarFun : INIT_VAR_FUN.values()) {
            TruffleDLL.ensureParsed("libR", initVarFun.funName, true);
            initVarFun.symbolHandle = new SymbolHandle(context.getEnv().importSymbol("@" + initVarFun.funName));
        }
        VirtualFrame frame = TruffleRFFIFrameHelper.create();
        Node executeNode = Message.createExecute(2).createNode();
        RFFIVariables[] variables = RFFIVariables.values();
        for (int i = 0; i < variables.length; i++) {
            RFFIVariables var = variables[i];
            Object value = var.getValue();
            if (value == null) {
                continue;
            }
            try {
                if (value instanceof Double) {
                    ForeignAccess.sendExecute(executeNode, frame, INIT_VAR_FUN.DOUBLE.symbolHandle.asTruffleObject(), i, value);
                } else if (value instanceof Integer) {
                    ForeignAccess.sendExecute(executeNode, frame, INIT_VAR_FUN.INT.symbolHandle.asTruffleObject(), i, value);
                } else {
                    // TODO
                    // ForeignAccess.sendExecute(executeNode, frame,
                    // INIT_VAR_FUN.OBJ.symbolHandle.asTruffleObject(), i, value);
                }
            } catch (Throwable t) {
                throw RInternalError.shouldNotReachHere(t);
            }
        }
    }

    public static class InvokeJNI extends Node {
        @Child JNI_CallRFFINode jniCall = new JNI_CallRFFINode();

        public Object invokeCall(NativeCallInfo nativeCallInfo, Object[] args) {
            return jniCall.invokeCall(nativeCallInfo, args);
        }

        public Object invokeVoidCall(NativeCallInfo nativeCallInfo, Object[] args) {
            jniCall.invokeVoidCall(nativeCallInfo, args);
            return RNull.instance;
        }
    }

    /**
     * Experimentally the node created for the message send contains cached information regarding
     * the target, which is {@link RContext} specific, leading to invalid data being accessed in
     * SHARED_PARENT_RW contexts (specifically the cached exported symbols used for package
     * initialization). So we guard the node with a check that the context has not changed.
     *
     */
    @ImportStatic({Message.class, RContext.class})
    public abstract static class InvokeTruffle extends Node {
        public abstract Object execute(NativeCallInfo nativeCallInfo, Object[] args, RContext context);

        @Specialization(guards = {"context == cachedContext"})
        protected Object invokeCallCached(NativeCallInfo nativeCallInfo, Object[] args, @SuppressWarnings("unused") RContext context,
                        @SuppressWarnings("unused") @Cached("getInstance()") RContext cachedContext,
                        @Cached("createExecute(0).createNode()") Node messageNode,
                        @SuppressWarnings("unused") @Cached("ensureReady(nativeCallInfo)") boolean ready) {
            return doInvoke(messageNode, nativeCallInfo, args);
        }

        @Specialization(contains = "invokeCallCached")
        protected Object invokeCallNormal(NativeCallInfo nativeCallInfo, Object[] args, @SuppressWarnings("unused") RContext context) {
            return doInvoke(Message.createExecute(0).createNode(), nativeCallInfo, args);
        }

        private static Object doInvoke(Node messageNode, NativeCallInfo nativeCallInfo, Object[] args) {
            VirtualFrame frame = TruffleRFFIFrameHelper.create();
            try {
                return ForeignAccess.sendExecute(messageNode, frame, nativeCallInfo.address.asTruffleObject(), args);
            } catch (Throwable t) {
                throw RInternalError.shouldNotReachHere(t);
            }
        }

        public static boolean ensureReady(NativeCallInfo nativeCallInfo) {
            TruffleDLL.ensureParsed(nativeCallInfo);
            ContextStateImpl contextState = TruffleRFFIContextState.getContextState().callState;
            if (!contextState.initVariablesDone) {
                initVariables(contextState.context);
                contextState.initVariablesDone = true;
            }
            return true;
        }

        public static InvokeTruffle create() {
            return InvokeTruffleNodeGen.create();
        }
    }

    /**
     * This class exists to separate out the delegated JNI calls from the Truffle calls.
     */
    public abstract static class SplitTruffleCallRFFINode extends Node {
        public abstract Object execute(NativeCallInfo nativeCallInfo, Object[] args, boolean voidCall);

        @Specialization(guards = {"isJNICall(nativeCallInfo)", "!voidCall"})
        protected Object invokeCall(NativeCallInfo nativeCallInfo, Object[] args, @SuppressWarnings("unused") boolean voidCall,
                        @Cached("new()") InvokeJNI invokeJNI) {
            return invokeJNI.invokeCall(nativeCallInfo, args);

        }

        @Specialization(guards = {"isJNICall(nativeCallInfo)", "voidCall"})
        protected Object invokeVoidCall(NativeCallInfo nativeCallInfo, Object[] args, @SuppressWarnings("unused") boolean voidCall,
                        @Cached("new()") InvokeJNI invokeJNI) {
            return invokeJNI.invokeVoidCall(nativeCallInfo, args);

        }

        @Specialization(guards = "!isJNICall(nativeCallInfo)")
        protected Object invokeCall(NativeCallInfo nativeCallInfo, Object[] args, @SuppressWarnings("unused") boolean voidCall,
                        @Cached("create()") InvokeTruffle invokeTruffle) {
            return invokeTruffle.execute(nativeCallInfo, args, RContext.getInstance());
        }

        public static boolean isJNICall(NativeCallInfo nativeCallInfo) {
            return nativeCallInfo.address.value instanceof Long;
        }
    }

    private static class TruffleCallRFFINode extends CallRFFINode {
        @Child SplitTruffleCallRFFINode splitTruffleCallRFFINode = SplitTruffleCallRFFINodeGen.create();

        @Override
        public synchronized Object invokeCall(NativeCallInfo nativeCallInfo, Object[] args) {
            return splitTruffleCallRFFINode.execute(nativeCallInfo, args, false);

        }

        @Override
        public synchronized void invokeVoidCall(NativeCallInfo nativeCallInfo, Object[] args) {
            splitTruffleCallRFFINode.execute(nativeCallInfo, args, true);
        }

        @Override
        public void setTempDir(String tempDir) {
            // TODO Truffleize
            new JNI_CallRFFINode().setTempDir(tempDir);
        }

        @Override
        public void setInteractive(boolean interactive) {
            // TODO Truffleize
            new JNI_CallRFFINode().setInteractive(interactive);
        }
    }

    /**
     * Upcalled from Rinternal et al.
     *
     * @param function
     */
    public void unimplemented(String function) {
        throw RInternalError.unimplemented("RFFI function: '" + function + "' not implemented");
    }

    @Override
    public CallRFFINode createCallRFFINode() {
        return new TruffleCallRFFINode();
    }
}
