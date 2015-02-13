/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.fastr;

import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * The entry point to all the {@code fastr.xxx} functions called by the {@code .FastR} primitive.
 */
public class FastRFunctionEntry {
    public static Object invoke(String name, Object[] argValues, RBuiltinNode fastRNode) {
        Object arg0 = argValues[0];
        if (name.equals("typeof")) {
            return arg0.getClass().getSimpleName();
        } else if (name.equals("stacktrace")) {
            fastRNode.forceVisibility(false);
            return FastRStackTrace.printStackTrace(checkLogical(argValues[0], fastRNode));
        }
        // The remainder all take a func argument
        RFunction func = checkFunction(arg0, fastRNode);
        switch (name) {
            case "createcc":
                fastRNode.forceVisibility(false);
                return FastRCallCounting.createCallCounter(func);
            case "getcc":
                return FastRCallCounting.getCallCount(func);

            case "compile":
                return FastRCompile.compileFunction(func, checkLogical(argValues[1], fastRNode));

            case "dumptrees":
                fastRNode.forceVisibility(false);
                return FastRDumpTrees.dump(func, checkLogical(argValues[1], fastRNode), checkLogical(argValues[2], fastRNode));

            case "source":
                return FastRSource.debugSource(func);

            case "tree":
                return FastRTree.printTree(func, checkLogical(argValues[1], fastRNode));

            case "syntaxtree":
                fastRNode.forceVisibility(false);
                return FastRSyntaxTree.printTree(func);

            case "seqlengths":
                return FastRSyntaxTree.printTree(func);

            default:
                throw RInternalError.shouldNotReachHere();
        }

    }

    private static RFunction checkFunction(Object arg, RBuiltinNode fastRNode) throws RError {
        if (arg instanceof RFunction) {
            return (RFunction) arg;
        } else {
            throw RError.error(fastRNode.getEncapsulatingSourceSection(), RError.Message.TYPE_EXPECTED, "function");
        }
    }

    private static byte checkLogical(Object arg, RBuiltinNode fastRNode) throws RError {
        if (arg instanceof Byte) {
            return (byte) arg;
        } else {
            throw RError.error(fastRNode.getEncapsulatingSourceSection(), RError.Message.TYPE_EXPECTED, "logical");
        }
    }

}