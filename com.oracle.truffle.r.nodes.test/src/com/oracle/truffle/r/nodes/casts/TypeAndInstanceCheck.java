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
package com.oracle.truffle.r.nodes.casts;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Optional;

import com.oracle.truffle.r.nodes.casts.CastUtils.Cast;
import com.oracle.truffle.r.nodes.casts.CastUtils.Cast.Coverage;
import com.oracle.truffle.r.nodes.casts.CastUtils.Casts;

public interface TypeAndInstanceCheck {
    boolean isInstance(Object x);

    Optional<Class<?>> classify();

    static Coverage coverage(Type from, Type to, boolean includeImplicits) {
        assert (to instanceof Not || to instanceof Class);
        assert (from instanceof Not || from instanceof Class);

        if (Not.negateType(to).equals(from)) {
            return Coverage.none;
        }

        boolean toPositive = !Not.isNegative(to);
        boolean fromPositive = !Not.isNegative(from);
        Class<?> positiveToCls = (Class<?>) CastUtils.Casts.boxType(Not.getPositiveType(to));
        Class<?> positiveFromCls = (Class<?>) CastUtils.Casts.boxType(Not.getPositiveType(from));

        Cast.Coverage positiveCoverage;
        if (((Class<?>) positiveToCls).isAssignableFrom(positiveFromCls)) {
            positiveCoverage = Cast.Coverage.full;
        } else if (((Class<?>) positiveFromCls).isAssignableFrom(positiveToCls)) {
            positiveCoverage = Cast.Coverage.partial;
        } else if (positiveToCls.isInterface() && (positiveFromCls.isInterface() || !(Modifier.isFinal(positiveFromCls.getModifiers()) || !Modifier.isAbstract(positiveFromCls.getModifiers())))) {
            positiveCoverage = Cast.Coverage.potential;
        } else if (positiveFromCls.isInterface() && (positiveToCls.isInterface() || !(Modifier.isFinal(positiveToCls.getModifiers()) || !Modifier.isAbstract(positiveToCls.getModifiers())))) {
            positiveCoverage = Cast.Coverage.potential;
        } else {
            positiveCoverage = Cast.Coverage.none;
        }

        Cast.Coverage implicitCvrg = Cast.Coverage.none;
        if (includeImplicits && Casts.hasImplicitCast(positiveFromCls, positiveToCls)) {
            implicitCvrg = Cast.Coverage.potential;
        }

        positiveCoverage = implicitCvrg.or(positiveCoverage);

        return positiveCoverage.transpose(positiveFromCls, positiveToCls, fromPositive, toPositive);
    }

}
