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
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetClassAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetNamesAttributeNode;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RAttributeStorage;

/**
 * This node is responsible for retrieving a value from the predefined (fixed) attribute. It accepts
 * both {@link DynamicObject} and {@link RAttributable} instances as the first argument. If the
 * first argument is {@link RAttributable} and its attributes are initialized, the recursive
 * instance of this class is used to get the attribute value from the attributes.
 */
public abstract class GetFixedAttributeNode extends FixedAttributeAccessNode {

    @Child private GetFixedAttributeNode recursive;

    protected GetFixedAttributeNode(String name) {
        super(name);
    }

    public static GetFixedAttributeNode create(String name) {
        if (SpecialAttributesFunctions.IsSpecialAttributeNode.isSpecialAttribute(name)) {
            return SpecialAttributesFunctions.createGetSpecialAttributeNode(name);
        } else {
            return GetFixedAttributeNodeGen.create(name);
        }
    }

    public static GetFixedAttributeNode createNames() {
        return GetNamesAttributeNode.create();
    }

    public static GetDimAttributeNode createDim() {
        return GetDimAttributeNode.create();
    }

    public static GetClassAttributeNode createClass() {
        return GetClassAttributeNode.create();
    }

    public abstract Object execute(Object attr);

    protected boolean hasProperty(Shape shape) {
        return shape.hasProperty(name);
    }

    @Specialization(limit = "3", //
                    guards = {"shapeCheck(shape, attrs)"}, //
                    assumptions = {"shape.getValidAssumption()"})
    protected Object getAttrCached(DynamicObject attrs,
                    @Cached("lookupShape(attrs)") Shape shape,
                    @Cached("lookupLocation(shape, name)") Location location) {
        return location == null ? null : location.get(attrs, shape);
    }

    @Specialization(contains = "getAttrCached")
    @TruffleBoundary
    protected Object getAttrFallback(DynamicObject attrs) {
        return attrs.get(name);
    }

    @Specialization
    protected Object getAttrFromAttributable(RAttributable x,
                    @Cached("create()") BranchProfile attrNullProfile,
                    @Cached("createBinaryProfile()") ConditionProfile attrStorageProfile,
                    @Cached("createClassProfile()") ValueProfile xTypeProfile) {

        DynamicObject attributes;
        if (attrStorageProfile.profile(x instanceof RAttributeStorage)) {
            attributes = ((RAttributeStorage) x).getAttributes();
        } else {
            attributes = xTypeProfile.profile(x).getAttributes();
        }

        if (attributes == null) {
            attrNullProfile.enter();
            return null;
        }

        if (recursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursive = insert(create(name));
        }

        return recursive.execute(attributes);
    }
}
