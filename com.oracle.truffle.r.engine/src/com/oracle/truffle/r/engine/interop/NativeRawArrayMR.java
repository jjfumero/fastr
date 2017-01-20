/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.engine.interop;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.engine.TruffleRLanguage;

@MessageResolution(receiverType = NativeRawArray.class, language = TruffleRLanguage.class)
public class NativeRawArrayMR {
    @Resolve(message = "READ")
    public abstract static class NRAReadNode extends Node {
        protected byte access(NativeRawArray receiver, int index) {
            return receiver.bytes[index];
        }
    }

    @Resolve(message = "WRITE")
    public abstract static class NRAWriteNode extends Node {
        protected Object access(NativeRawArray receiver, int index, byte value) {
            receiver.bytes[index] = value;
            return value;
        }
    }

    @Resolve(message = "GET_SIZE")
    public abstract static class NRAGetSizeNode extends Node {
        protected int access(NativeRawArray receiver) {
            return receiver.bytes.length;
        }
    }

    @Resolve(message = "UNBOX")
    public abstract static class NRAUnboxNode extends Node {
        protected long access(NativeRawArray receiver) {
            return receiver.convertToNative();
        }
    }

    @CanResolve
    public abstract static class NRACheck extends Node {

        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof NativeRawArray;
        }
    }
}
