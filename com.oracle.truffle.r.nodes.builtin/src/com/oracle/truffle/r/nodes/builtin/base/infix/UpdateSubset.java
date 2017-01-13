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
package com.oracle.truffle.r.nodes.builtin.base.infix;

import static com.oracle.truffle.r.nodes.builtin.base.infix.SpecialsUtils.convertIndex;
import static com.oracle.truffle.r.nodes.builtin.base.infix.SpecialsUtils.profile;
import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import java.util.Arrays;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.EmptyTypeSystemFlatLayout;
import com.oracle.truffle.r.nodes.access.vector.ElementAccessMode;
import com.oracle.truffle.r.nodes.access.vector.ReplaceVectorNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.infix.SpecialsUtils.ConvertIndex;
import com.oracle.truffle.r.nodes.builtin.base.infix.SpecialsUtils.ProfiledValue;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.nodes.RNode;

@RBuiltin(name = "[<-", kind = PRIMITIVE, parameterNames = {"", "..."}, dispatch = INTERNAL_GENERIC, behavior = PURE)
@TypeSystemReference(EmptyTypeSystemFlatLayout.class)
public abstract class UpdateSubset extends RBuiltinNode {

    @Child private ReplaceVectorNode replaceNode = ReplaceVectorNode.create(ElementAccessMode.SUBSET, false);
    private final ConditionProfile argsLengthLargerThanOneProfile = ConditionProfile.createBinaryProfile();

    public static RNode special(ArgumentsSignature signature, RNode[] args, boolean inReplacement) {
        if (SpecialsUtils.isCorrectUpdateSignature(signature) && (args.length == 3 || args.length == 4)) {
            ProfiledValue vector = profile(args[0]);
            ConvertIndex index = convertIndex(args[1]);
            if (args.length == 3) {
                return UpdateSubscriptSpecialNodeGen.create(inReplacement, vector, index, args[2]);
            } else {
                return UpdateSubscriptSpecial2NodeGen.create(inReplacement, vector, index, convertIndex(args[2]), args[3]);
            }
        }
        return null;
    }

    @Specialization(guards = "!args.isEmpty()")
    protected Object update(VirtualFrame frame, Object x, RArgsValuesAndNames args) {
        Object value = args.getArgument(args.getLength() - 1);
        Object[] pos;
        if (argsLengthLargerThanOneProfile.profile(args.getLength() > 1)) {
            pos = Arrays.copyOf(args.getArguments(), args.getLength() - 1);
        } else {
            pos = new Object[]{RMissing.instance};
        }
        return replaceNode.apply(frame, x, pos, value);
    }

    @Specialization(guards = "args.isEmpty()")
    @SuppressWarnings("unused")
    protected Object getNoInd(Object x, RArgsValuesAndNames args) {
        throw RError.error(this, RError.Message.INVALID_ARG_NUMBER, "SubAssignArgs");
    }
}
