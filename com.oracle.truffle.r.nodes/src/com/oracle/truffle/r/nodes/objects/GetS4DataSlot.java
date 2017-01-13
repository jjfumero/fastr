/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1996, 1997  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1995-2014, The R Core Team
 * Copyright (c) 2002-2008, The R Foundation
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.r.nodes.attributes.GetFixedAttributeNode;
import com.oracle.truffle.r.nodes.attributes.RemoveFixedAttributeNode;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.SetClassAttributeNode;
import com.oracle.truffle.r.nodes.unary.CastToVectorNode;
import com.oracle.truffle.r.nodes.unary.TypeofNode;
import com.oracle.truffle.r.nodes.unary.TypeofNodeGen;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RAttributable;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RShareable;
import com.oracle.truffle.r.runtime.data.RTypedValue;

// transcribed from src/main/attrib.c
public abstract class GetS4DataSlot extends Node {

    public abstract RTypedValue executeObject(RAttributable attObj);

    @Child private GetFixedAttributeNode s3ClassAttrAccess;
    @Child private RemoveFixedAttributeNode s3ClassAttrRemove;
    @Child private CastToVectorNode castToVector;
    @Child private GetFixedAttributeNode dotDataAttrAccess;
    @Child private GetFixedAttributeNode dotXDataAttrAccess;
    @Child private TypeofNode typeOf = TypeofNodeGen.create();
    @Child private SetClassAttributeNode setClassAttrNode;

    private final BranchProfile shareable = BranchProfile.create();

    private final RType type;

    protected GetS4DataSlot(RType type) {
        this.type = type;
    }

    @Specialization
    protected RTypedValue doNewObject(RAttributable attrObj) {
        RAttributable obj = attrObj;
        Object value = null;
        if (!(obj instanceof RS4Object) || type == RType.S4Object) {
            Object s3Class = null;
            DynamicObject attributes = obj.getAttributes();
            if (attributes != null) {
                if (s3ClassAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    s3ClassAttrAccess = insert(GetFixedAttributeNode.create(RRuntime.DOT_S3_CLASS));
                }
                s3Class = s3ClassAttrAccess.execute(attributes);
            }
            if (s3Class == null && type == RType.S4Object) {
                return RNull.instance;
            }
            if (obj instanceof RShareable && ((RShareable) obj).isShared()) {
                shareable.enter();
                obj = (RAttributable) ((RShareable) obj).copy();
            }

            if (setClassAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setClassAttrNode = insert(SetClassAttributeNode.create());
            }

            if (s3Class != null) {
                if (s3ClassAttrRemove == null) {
                    assert castToVector == null;
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    s3ClassAttrRemove = insert(RemoveFixedAttributeNode.create(RRuntime.DOT_S3_CLASS));
                    castToVector = insert(CastToVectorNode.create());

                }
                s3ClassAttrRemove.execute(obj.initAttributes());
                setClassAttrNode.execute(obj, castToVector.execute(s3Class));
            } else {
                setClassAttrNode.reset(obj);
            }
            obj.unsetS4();
            if (type == RType.S4Object) {
                return obj;
            }
            value = obj;
        } else {
            DynamicObject attributes = obj.getAttributes();
            if (attributes != null) {
                if (dotDataAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dotDataAttrAccess = insert(GetFixedAttributeNode.create(RRuntime.DOT_DATA));
                }
                value = dotDataAttrAccess.execute(attributes);
            }
        }
        if (value == null) {
            DynamicObject attributes = obj.getAttributes();
            if (attributes != null) {
                if (dotXDataAttrAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dotXDataAttrAccess = insert(GetFixedAttributeNode.create(RRuntime.DOT_XDATA));
                }
                value = dotXDataAttrAccess.execute(attributes);
            }
        }
        if (value != null && (type == RType.Any || type == typeOf.execute(value))) {
            return (RTypedValue) value;
        } else {
            return RNull.instance;
        }
    }
}
