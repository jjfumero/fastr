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
package com.oracle.truffle.r.runtime.data;

import java.util.Iterator;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.Utils;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.gnur.SEXPTYPE;

/**
 * Denotes the (rarely seen) {@code pairlist} type in R.
 *
 * {@code null} is never allowed as a value for the tag, car or cdr, only the type.
 */
public final class RPairList extends RSharingAttributeStorage implements RAbstractContainer, Iterable<RPairList> {
    private Object car = RNull.instance;
    private Object cdr = RNull.instance;
    /**
     * Externally, i.e., when serialized, this is either a SYMSXP ({@link RSymbol}) or an
     * {@link RNull}. Internally it may take on other, non-null, values.
     */
    private Object tag = RNull.instance;

    /**
     * Denotes the (GnuR) type of entity that the pairlist represents. (Internal use only).
     */
    private SEXPTYPE type;

    /**
     * Uninitialized pairlist.
     */
    RPairList() {
    }

    /**
     * Variant used in unserialization to record the GnuR type the pairlist denotes.
     */
    RPairList(Object car, Object cdr, Object tag, SEXPTYPE type) {
        assert car != null;
        assert cdr != null;
        assert tag != null;
        this.car = car;
        this.cdr = cdr;
        this.tag = tag;
        this.type = type;
    }

    /**
     * Creates a new pair list of given size > 0. Note: pair list of size 0 is NULL.
     */
    public static RPairList create(int size) {
        return create(size, null);
    }

    @TruffleBoundary
    public static RPairList create(int size, SEXPTYPE type) {
        assert size > 0 : "a pair list of size = 0 does not exist, it should be NULL";
        RPairList result = new RPairList();
        for (int i = 1; i < size; i++) {
            RPairList tmp = result;
            result = new RPairList();
            result.cdr = tmp;
        }
        if (type != null) {
            result.type = type;
        }
        return result;
    }

    @Override
    public RType getRType() {
        return RType.PairList;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("type=%s, tag=%s, car=%s, cdr=%s", type, tag, toStringHelper(car), toStringHelper(cdr));
    }

    private static String toStringHelper(Object obj) {
        return obj == null ? "null" : obj.getClass().getSimpleName();
    }

    /**
     * Convert to a {@link RList}.
     */
    // TODO (chumer) too complex for a non truffle boundary
    @TruffleBoundary
    public RList toRList() {
        int len = 0;
        boolean named = false;
        for (RPairList item : this) {
            named = named || !item.isNullTag();
            len++;
        }
        Object[] data = new Object[len];
        String[] names = named ? new String[len] : null;
        int i = 0;
        for (RPairList plt : this) {
            data[i] = plt.car;
            if (named) {
                Object ptag = plt.tag;
                if (isNull(ptag)) {
                    names[i] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
                } else if (ptag instanceof RSymbol) {
                    names[i] = ((RSymbol) ptag).getName();
                } else {
                    names[i] = RRuntime.asString(ptag);
                    assert names[i] != null : "unexpected type of tag in RPairList";
                }
            }
            i++;
        }
        RList result = named ? RDataFactory.createList(data, RDataFactory.createStringVector(names, RDataFactory.COMPLETE_VECTOR)) : RDataFactory.createList(data);
        DynamicObject attrs = getAttributes();
        if (attrs != null) {
            DynamicObject resultAttrs = result.initAttributes();
            Iterator<RAttributesLayout.RAttribute> iter = RAttributesLayout.asIterable(attrs).iterator();
            while (iter.hasNext()) {
                RAttributesLayout.RAttribute attr = iter.next();
                String attrName = attr.getName();
                if (!(attrName.equals(RRuntime.NAMES_ATTR_KEY) || attrName.equals(RRuntime.DIM_ATTR_KEY) || attrName.equals(RRuntime.DIMNAMES_ATTR_KEY))) {
                    resultAttrs.define(attrName, attr.getValue());
                }
            }
        }
        return result;
    }

    public Object car() {
        return car;
    }

    public Object cdr() {
        return cdr;
    }

    public void setCar(Object newCar) {
        assert newCar != null;
        car = newCar;
    }

    public void setCdr(Object newCdr) {
        assert newCdr != null;
        cdr = newCdr;
    }

    public Object cadr() {
        RPairList cdrpl = (RPairList) cdr;
        return cdrpl.car;
    }

    public Object cddr() {
        RPairList cdrpl = (RPairList) cdr;
        return cdrpl.cdr;
    }

    public Object caddr() {
        RPairList pl = (RPairList) cddr();
        return pl.car;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object newTag) {
        assert newTag != null;
        this.tag = newTag;
    }

    public void setType(SEXPTYPE type) {
        assert this.type == null || this.type.equals(type);
        this.type = type;
    }

    public boolean isNullTag() {
        return tag == RNull.instance;
    }

    public SEXPTYPE getType() {
        return type;
    }

    @Override
    public boolean isComplete() {
        // TODO: is it important to get more precise information here?
        return false;
    }

    @Override
    public int getLength() {
        int result = 1;
        Object tcdr = cdr;
        while (!isNull(tcdr)) {
            if (tcdr instanceof RPairList) {
                tcdr = ((RPairList) tcdr).cdr;
            }
            result++;
        }
        return result;
    }

    @Override
    public RAbstractContainer resize(int size) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public boolean hasDimensions() {
        return true;
    }

    @Override
    public int[] getDimensions() {
        return new int[]{1};
    }

    @Override
    public void setDimensions(int[] newDimensions) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public Class<?> getElementClass() {
        return null;
    }

    @Override
    public RShareable copy() {
        RPairList result = new RPairList();
        Object original = this;
        while (!isNull(original)) {
            RPairList origList = (RPairList) original;
            result.car = origList.car;
            result.tag = origList.tag;
            result.cdr = new RPairList();
            result = (RPairList) result.cdr;
            original = origList.cdr;
        }
        if (getAttributes() != null) {
            result.initAttributes(RAttributesLayout.copy(getAttributes()));
        }
        return result;
    }

    @Override
    public RShareable deepCopy() {
        RInternalError.shouldNotReachHere();
        return null;
    }

    @Override
    public RShareable materializeToShareable() {
        RInternalError.shouldNotReachHere();
        return null;
    }

    @Override
    public Object getDataAtAsObject(int index) {
        RPairList pl = this;
        int i = 0;
        while (!isNull(pl) && i < index) {
            pl = (RPairList) pl.cdr;
            i++;
        }
        return pl.car;
    }

    public static boolean isNull(Object obj) {
        return obj == RNull.instance;
    }

    @Override
    public RStringVector getNames() {
        int l = getLength();
        String[] data = new String[l];
        RPairList pl = this;
        boolean complete = RDataFactory.COMPLETE_VECTOR;
        int i = 0;
        while (true) {
            data[i] = Utils.toString(pl.tag);
            if (pl.tag == RRuntime.STRING_NA) {
                complete = false;
            }
            if (isNull(pl.cdr)) {
                break;
            }
            pl = (RPairList) pl.cdr;
            i++;
        }
        return RDataFactory.createStringVector(data, complete);
    }

    @Override
    public void setNames(RStringVector newNames) {
        Object p = this;
        for (int i = 0; i < newNames.getLength() && !isNull(p); i++) {
            RPairList pList = (RPairList) p;
            pList.tag = newNames.getDataAt(i);
            p = pList.cdr;
        }
    }

    @Override
    public RList getDimNames() {
        return null;
    }

    @Override
    public void setDimNames(RList newDimNames) {
        throw RInternalError.unimplemented();
    }

    @Override
    public void setRowNames(RAbstractVector rowNames) {
        throw RInternalError.unimplemented();
    }

    @Override
    public RStringVector getImplicitClass() {
        return RDataFactory.createStringVector(RType.PairList.getName());
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public Iterator<RPairList> iterator() {
        return new Iterator<RPairList>() {
            private Object plt = RPairList.this;

            @Override
            public boolean hasNext() {
                return !isNull(plt);
            }

            @Override
            public RPairList next() {
                assert plt instanceof RPairList;
                RPairList curr = (RPairList) plt;
                plt = curr.cdr;
                return curr;
            }
        };
    }
}
