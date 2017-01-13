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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.complexValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.numericValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetDimNamesAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.SetDimNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDoubleVector;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "crossprod", kind = INTERNAL, parameterNames = {"x", "y"}, behavior = PURE)
public abstract class Crossprod extends RBuiltinNode {

    @Child private MatMult matMult = MatMultNodeGen.create(/* promoteDimNames: */ false);
    @Child private Transpose transpose;

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.arg("x").mustBe(numericValue().or(complexValue()), RError.ROOTNODE, RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR);
        casts.arg("y").defaultError(RError.ROOTNODE, RError.Message.NUMERIC_COMPLEX_MATRIX_VECTOR).allowNull().mustBe(numericValue().or(complexValue()));
    }

    private Object matMult(Object op1, Object op2) {
        return matMult.executeObject(op1, op2);
    }

    private Object transpose(RAbstractVector value) {
        if (transpose == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            transpose = insert(TransposeNodeGen.create());
        }
        return transpose.execute(value);
    }

    @Specialization(guards = {"x.isMatrix()", "y.isMatrix()"})
    protected RDoubleVector crossprod(RAbstractDoubleVector x, RAbstractDoubleVector y,
                    @Cached("create()") GetDimAttributeNode getXDimsNode,
                    @Cached("create()") GetDimAttributeNode getYDimsNode,
                    @Cached("create()") SetDimNamesAttributeNode setDimNamesNode,
                    @Cached("create()") GetDimNamesAttributeNode getADimNamesNode,
                    @Cached("create()") GetDimNamesAttributeNode getBDimNamesNode) {
        int[] xDims = getXDimsNode.getDimensions(x);
        int[] yDims = getYDimsNode.getDimensions(y);
        int xRows = xDims[0];
        int xCols = xDims[1];
        int yRows = yDims[0];
        int yCols = yDims[1];
        return matMult.doubleMatrixMultiply(x, y, xCols, xRows, yRows, yCols, xRows, 1, 1, yRows, false, setDimNamesNode, getADimNamesNode, getBDimNamesNode);
    }

    private static RDoubleVector mirror(RDoubleVector result, GetDimAttributeNode getResultDimsNode) {
        /*
         * Mirroring the result is not only good for performance, but it is also required to produce
         * the same result as GNUR.
         */
        int[] resultDims = getResultDimsNode.getDimensions(result);
        assert result.isMatrix() && resultDims[0] == resultDims[1];
        int size = resultDims[0];
        double[] data = result.getDataWithoutCopying();
        for (int row = 0; row < size; row++) {
            int destIndex = row * size + row + 1;
            int sourceIndex = (row + 1) * size + row;
            for (int col = row + 1; col < size; col++) {
                data[destIndex] = data[sourceIndex];
                destIndex++;
                sourceIndex += size;
            }
        }
        return result;
    }

    @Specialization
    protected Object crossprod(RAbstractVector x, RAbstractVector y) {
        return matMult(transpose(x), y);
    }

    @Specialization(guards = "x.isMatrix()")
    protected RDoubleVector crossprodDoubleMatrix(RAbstractDoubleVector x, @SuppressWarnings("unused") RNull y,
                    @Cached("create()") GetDimAttributeNode getDimsNode,
                    @Cached("create()") GetDimAttributeNode getResultDimsNode,
                    @Cached("create()") SetDimNamesAttributeNode setDimNamesNode,
                    @Cached("create()") GetDimNamesAttributeNode getADimNamesNode,
                    @Cached("create()") GetDimNamesAttributeNode getBDimNamesNode) {
        int[] xDims = getDimsNode.getDimensions(x);
        int xRows = xDims[0];
        int xCols = xDims[1];
        return mirror(matMult.doubleMatrixMultiply(x, x, xCols, xRows, xRows, xCols, xRows, 1, 1, xRows, true, setDimNamesNode, getADimNamesNode, getBDimNamesNode), getResultDimsNode);
    }

    @Specialization
    protected Object crossprod(RAbstractVector x, @SuppressWarnings("unused") RNull y) {
        return matMult(transpose(x), x);
    }
}
