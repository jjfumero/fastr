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
package com.oracle.truffle.r.nodes.access;

import static com.oracle.truffle.api.nodes.NodeCost.NONE;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.nodes.access.WriteVariableNode.Mode;
import com.oracle.truffle.r.nodes.control.OperatorNode;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.ArgumentsSignature;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxElement;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;

@NodeInfo(cost = NONE)
public final class WriteVariableSyntaxNode extends OperatorNode {

    @Child private WriteVariableNode write;
    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    private final RSyntaxElement lhs;

    public WriteVariableSyntaxNode(SourceSection source, RSyntaxLookup operator, RSyntaxElement lhs, RNode rhs, boolean isSuper) {
        super(source, operator);
        this.lhs = lhs;
        String name;
        if (lhs instanceof RSyntaxLookup) {
            name = ((RSyntaxLookup) lhs).getIdentifier();
        } else if (lhs instanceof RSyntaxConstant) {
            RSyntaxConstant c = (RSyntaxConstant) lhs;
            if (c.getValue() instanceof String) {
                name = (String) c.getValue();
            } else {
                // "this" needs to be initialized for error reporting to work
                this.write = WriteVariableNode.createAnonymous("dummy", rhs, Mode.REGULAR, isSuper);
                throw RError.error(this, RError.Message.INVALID_LHS, "do_set");
            }
        } else {
            throw RInternalError.unimplemented("unexpected lhs type in replacement: " + lhs.getClass());
        }
        this.write = WriteVariableNode.createAnonymous(name, rhs, Mode.REGULAR, isSuper);
        assert write != null;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object result = write.execute(frame);
        visibility.execute(frame, false);
        return result;
    }

    @Override
    public RSyntaxElement[] getSyntaxArguments() {
        return new RSyntaxElement[]{lhs, write.getRhs().asRSyntaxNode()};
    }

    @Override
    public ArgumentsSignature getSyntaxSignature() {
        return ArgumentsSignature.empty(2);
    }
}
