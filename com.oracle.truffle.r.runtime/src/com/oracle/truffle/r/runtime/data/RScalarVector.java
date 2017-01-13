/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public abstract class RScalarVector extends RScalar implements RAbstractVector {

    public abstract boolean isNA();

    @Override
    public final RStringVector getClassHierarchy() {
        return RDataFactory.createStringVector(getRType().getName());
    }

    @Override
    public final RStringVector getImplicitClass() {
        return RDataFactory.createStringVector(getRType().getName());
    }

    @Override
    public final RScalarVector copy() {
        return this;
    }

    @Override
    public void setComplete(boolean complete) {
        // scalar vectors don't need this information.
        // it is always rechecked
    }

    @Override
    public final boolean isComplete() {
        return !isNA();
    }

    @Override
    public final int getLength() {
        return 1;
    }

    @Override
    public RAbstractContainer resize(int size) {
        return materialize().resize(size);
    }

    @Override
    public boolean hasDimensions() {
        return false;
    }

    @Override
    public int[] getDimensions() {
        return null;
    }

    @Override
    public void setDimensions(int[] newDimensions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RTypedValue getNonShared() {
        return materialize().getNonShared();
    }

    @Override
    public RShareable materializeToShareable() {
        return materialize().materializeToShareable();
    }

    @Override
    public RStringVector getNames() {
        return null;
    }

    @Override
    public void setNames(RStringVector newNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RList getDimNames() {
        return null;
    }

    @Override
    public void setDimNames(RList newDimNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getRowNames() {
        return null;
    }

    @Override
    public void setRowNames(RAbstractVector rowNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isObject() {
        return false;
    }

    @Override
    public DynamicObject initAttributes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void initAttributes(DynamicObject newAttributes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DynamicObject getAttributes() {
        return null;
    }

    @Override
    public RVector<?> copyResized(int size, boolean fillNA) {
        RVector<?> result = materialize().copyResized(size, fillNA);
        MemoryCopyTracer.reportCopying(this, result);
        return result;
    }

    @Override
    public RAbstractVector copyWithNewDimensions(int[] newDimensions) {
        RAbstractVector result = materialize().copyWithNewDimensions(newDimensions);
        MemoryCopyTracer.reportCopying(this, result);
        return result;
    }

    @Override
    @TruffleBoundary
    public RVector<?> copyResizedWithDimensions(int[] newDimensions, boolean fillNA) {
        RVector<?> result = materialize().copyResizedWithDimensions(newDimensions, fillNA);
        MemoryCopyTracer.reportCopying(this, result);
        return result;
    }

    @Override
    public RAbstractVector copyDropAttributes() {
        RVector<?> result = materialize().copyDropAttributes();
        MemoryCopyTracer.reportCopying(this, result);
        return result;
    }

    @Override
    public RVector<?> createEmptySameType(int newLength, boolean newIsComplete) {
        return materialize().createEmptySameType(newLength, newIsComplete);
    }

    @Override
    public boolean isMatrix() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public final boolean checkCompleteness() {
        return isComplete();
    }
}
