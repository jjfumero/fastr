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
package com.oracle.truffle.r.nodes.control;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.nodes.unary.ConvertBooleanNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

public final class IfNode extends OperatorNode {

    @Child private ConvertBooleanNode condition;
    @Child private RNode thenPart;
    @Child private RNode elsePart;
    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

    public IfNode(SourceSection src, RSyntaxLookup operator, RSyntaxNode condition, RSyntaxNode thenPart, RSyntaxNode elsePart) {
        super(src, operator);
        this.condition = ConvertBooleanNode.create(condition);
        this.thenPart = thenPart.asRNode();
        this.elsePart = elsePart == null ? null : elsePart.asRNode();
    }

    /**
     * Result visibility of an {@code if} expression is not only a property of the {@code if}
     * builtin; it also depends on whether there is an else branch or not, and on the condition. For
     * instance, the expression {@code if (FALSE) 23} will evaluate to {@code NULL}, but the result
     * will not be printed in the shell. Conversely, {@code NULL} will be printed for
     * {@code if (FALSE) 23 else NULL} because the else branch is given.
     */

    @Override
    public Object execute(VirtualFrame frame) {
        byte cond = condition.executeByte(frame);
        visibility.execute(frame, elsePart != null || cond == RRuntime.LOGICAL_TRUE);

        if (cond == RRuntime.LOGICAL_NA) {
            // NA is the only remaining option
            CompilerDirectives.transferToInterpreter();
            throw RError.error(this, RError.Message.NA_UNEXP);
        }

        if (conditionProfile.profile(cond == RRuntime.LOGICAL_TRUE)) {
            return thenPart.execute(frame);
        } else {
            assert cond == RRuntime.LOGICAL_FALSE : "logical value none of TRUE|FALSE|NA";

            if (elsePart != null) {
                return elsePart.execute(frame);
            } else {
                return RNull.instance;
            }
        }
    }

    public ConvertBooleanNode getCondition() {
        return condition;
    }

    public RNode getThenPart() {
        return thenPart;
    }

    public RNode getElsePart() {
        return elsePart;
    }

    @Override
    public RSyntaxElement[] getSyntaxArguments() {
        if (elsePart == null) {
            return new RSyntaxElement[]{condition.asRSyntaxNode(), thenPart.asRSyntaxNode()};
        } else {
            return new RSyntaxElement[]{condition.asRSyntaxNode(), thenPart.asRSyntaxNode(), elsePart.asRSyntaxNode()};
        }
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return ArgumentsSignature.empty(elsePart == null ? 2 : 3);
    }
}
