/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractComplexVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractLogicalVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractRawVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

public abstract class NonNANode extends CastNode {

    private final RBaseNode callObj;
    private final RError.Message message;
    private final Object[] messageArgs;
    private final Object naReplacement;

    private final BranchProfile warningProfile = BranchProfile.create();

    protected NonNANode(RBaseNode callObj, RError.Message message, Object[] messageArgs, Object naReplacement) {
        this.callObj = callObj == null ? this : callObj;
        this.message = message;
        this.messageArgs = messageArgs;
        this.naReplacement = naReplacement;
    }

    protected NonNANode(Object naReplacement) {
        this(null, null, null, naReplacement);
    }

    public Object getNAReplacement() {
        return naReplacement;
    }

    private Object handleNA(Object arg) {
        if (naReplacement != null) {
            if (message != null) {
                warningProfile.enter();
                handleArgumentWarning(arg, callObj, message, messageArgs);
            }
            return naReplacement;
        } else {
            handleArgumentError(arg, callObj, message, messageArgs);
            return null;
        }
    }

    @Specialization(guards = "!isLogicalNA(x)")
    protected Object onLogicalNonNA(byte x) {
        return x;
    }

    @Specialization(guards = "isLogicalNA(x)")
    protected Object onLogicalNA(byte x) {
        return handleNA(x);
    }

    protected boolean isLogicalNA(byte x) {
        return RRuntime.isNA(x);
    }

    @Specialization
    protected Object onBoolean(boolean x) {
        return x;
    }

    @Specialization(guards = "!isIntegerNA(x)")
    protected Object onIntegerNonNA(int x) {
        return x;
    }

    @Specialization(guards = "isIntegerNA(x)")
    protected Object onIntegerNA(int x) {
        return handleNA(x);
    }

    protected boolean isIntegerNA(int x) {
        return RRuntime.isNA(x);
    }

    @Specialization(guards = "!isDoubleNA(x)")
    protected Object onDoubleNonNA(double x) {
        return x;
    }

    @Specialization(guards = "isDoubleNA(x)")
    protected Object onDoubleNA(double x) {
        return handleNA(x);
    }

    protected boolean isDoubleNA(double x) {
        return RRuntime.isNAorNaN(x);
    }

    @Specialization(guards = "!isComplexNA(x)")
    protected Object onComplexNonNA(RComplex x) {
        return x;
    }

    @Specialization(guards = "isComplexNA(x)")
    protected Object onComplex(RComplex x) {
        return handleNA(x);
    }

    protected boolean isComplexNA(RComplex x) {
        return RRuntime.isNA(x);
    }

    @Specialization(guards = "!isStringNA(x)")
    protected Object onStringNonNA(String x) {
        return x;
    }

    @Specialization(guards = "isStringNA(x)")
    protected Object onStringNA(String x) {
        return handleNA(x);
    }

    protected boolean isStringNA(String x) {
        return RRuntime.isNA(x);
    }

    @Specialization
    protected Object onNull(RNull x) {
        return x;
    }

    protected boolean isComplete(RAbstractContainer x) {
        return x.isComplete();
    }

    @Specialization(guards = "isComplete(x)")
    protected Object onCompleteContainer(RAbstractContainer x) {
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractIntVector x) {
        int len = x.getLength();
        for (int i = 0; i < len; i++) {
            if (RRuntime.isNA(x.getDataAt(i))) {
                return handleNA(x);
            }
        }
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractLogicalVector x) {
        int len = x.getLength();
        for (int i = 0; i < len; i++) {
            if (RRuntime.isNA(x.getDataAt(i))) {
                return handleNA(x);
            }
        }
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractDoubleVector x) {
        int len = x.getLength();
        for (int i = 0; i < len; i++) {
            if (RRuntime.isNA(x.getDataAt(i))) {
                return handleNA(x);
            }
        }
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractComplexVector x) {
        int len = x.getLength();
        for (int i = 0; i < len; i++) {
            if (RRuntime.isNA(x.getDataAt(i))) {
                return handleNA(x);
            }
        }
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractStringVector x) {
        int len = x.getLength();
        for (int i = 0; i < len; i++) {
            if (RRuntime.isNA(x.getDataAt(i))) {
                return handleNA(x);
            }
        }
        return x;
    }

    @Specialization(guards = "!isComplete(x)")
    protected Object onPossiblyIncompleteContainer(RAbstractRawVector x) {
        return x;
    }
}
