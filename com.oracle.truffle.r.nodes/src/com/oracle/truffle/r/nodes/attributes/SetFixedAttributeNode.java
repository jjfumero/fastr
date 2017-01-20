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
package com.oracle.truffle.r.nodes.attributes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.FinalLocationException;
import com.oracle.truffle.api.object.IncompatibleLocationException;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.SetSpecialAttributeNode;
import com.oracle.truffle.r.nodes.function.opt.ShareObjectNode;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributeStorage;

/**
 * This node is responsible for setting a value to the predefined (fixed) attribute. It accepts both
 * {@link DynamicObject} and {@link RAttributable} instances as the first argument. If the first
 * argument is {@link RAttributable} and the attribute is a special one (i.e. names, dims, dimnames,
 * rownames), a corresponding node defined in the {@link SpecialAttributesFunctions} class is
 * created and the processing is delegated to it. If the first argument is {@link RAttributable} and
 * the attribute is not a special one, it is made sure that the attributes in the first argument are
 * initialized. Then the recursive instance of this class is used to set the attribute value to the
 * attributes.
 */
public abstract class SetFixedAttributeNode extends FixedAttributeAccessNode {

    @Child private SetFixedAttributeNode recursive;
    @Child private SetSpecialAttributeNode setSpecialAttrNode;

    protected SetFixedAttributeNode(String name) {
        super(name);
    }

    public static SetFixedAttributeNode create(String name) {
        if (SpecialAttributesFunctions.IsSpecialAttributeNode.isSpecialAttribute(name)) {
            return SpecialAttributesFunctions.createSetSpecialAttributeNode(name);
        } else {
            return SetFixedAttributeNodeGen.create(name);
        }
    }

    public static SetFixedAttributeNode createNames() {
        return SpecialAttributesFunctions.SetNamesAttributeNode.create();
    }

    public static SetFixedAttributeNode createDim() {
        return SpecialAttributesFunctions.SetDimAttributeNode.create();
    }

    public static SetFixedAttributeNode createDimNames() {
        return SpecialAttributesFunctions.SetDimNamesAttributeNode.create();
    }

    public static SetFixedAttributeNode createClass() {
        return SpecialAttributesFunctions.SetClassAttributeNode.create();
    }

    public abstract void execute(Object attr, Object value);

    @Specialization(limit = "3", //
                    guards = {"shapeCheck(shape, attrs)", "location != null", "canSet(location, value)"}, //
                    assumptions = {"shape.getValidAssumption()"})
    protected void setAttrCached(DynamicObject attrs, Object value,
                    @Cached("lookupShape(attrs)") Shape shape,
                    @Cached("lookupLocation(shape, name)") Location location) {
        try {
            location.set(attrs, value, shape);
        } catch (IncompatibleLocationException | FinalLocationException ex) {
            throw RInternalError.shouldNotReachHere(ex);
        }
    }

    @Specialization(limit = "3", //
                    guards = {"shapeCheck(oldShape, attrs)", "oldLocation == null", "canStore(newLocation, value)"}, //
                    assumptions = {"oldShape.getValidAssumption()", "newShape.getValidAssumption()"})
    protected static void setNewAttrCached(DynamicObject attrs, Object value,
                    @Cached("lookupShape(attrs)") Shape oldShape,
                    @SuppressWarnings("unused") @Cached("lookupLocation(oldShape, name)") Location oldLocation,
                    @Cached("defineProperty(oldShape, name, value)") Shape newShape,
                    @Cached("lookupLocation(newShape, name)") Location newLocation) {
        try {
            newLocation.set(attrs, value, oldShape, newShape);
        } catch (IncompatibleLocationException ex) {
            throw RInternalError.shouldNotReachHere(ex);
        }
    }

    protected static Shape defineProperty(Shape oldShape, Object name, Object value) {
        return oldShape.defineProperty(name, value, 0);
    }

    @Specialization(contains = "setAttrCached")
    @TruffleBoundary
    protected void setFallback(DynamicObject attrs, Object value) {
        attrs.define(name, value);
    }

    @Specialization
    protected void setAttrInAttributable(RAttributable x, Object value,
                    @Cached("create()") BranchProfile attrNullProfile,
                    @Cached("createBinaryProfile()") ConditionProfile attrStorageProfile,
                    @Cached("createClassProfile()") ValueProfile xTypeProfile,
                    @Cached("create()") ShareObjectNode updateRefCountNode) {
        DynamicObject attributes;
        if (attrStorageProfile.profile(x instanceof RAttributeStorage)) {
            attributes = ((RAttributeStorage) x).getAttributes();
        } else {
            attributes = xTypeProfile.profile(x).getAttributes();
        }

        if (attributes == null) {
            attrNullProfile.enter();
            attributes = x.initAttributes();
        }

        if (recursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursive = insert(create(name));
        }

        recursive.execute(attributes, value);

        updateRefCountNode.execute(value);
    }
}
