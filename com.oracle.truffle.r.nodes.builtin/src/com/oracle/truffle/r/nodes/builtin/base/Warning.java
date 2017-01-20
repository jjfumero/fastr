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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.RVisibility.OFF;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RErrorHandling;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;

@RBuiltin(name = "warning", visibility = OFF, kind = INTERNAL, parameterNames = {"call", "immediate", "nobreaks", "message"}, behavior = COMPLEX)
public abstract class Warning extends RBuiltinNode {

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.arg("call").asLogicalVector().findFirst().map(toBoolean());
        casts.arg("immediate").asLogicalVector().findFirst().map(toBoolean());
        casts.arg("nobreaks").asLogicalVector().findFirst().map(toBoolean());
        casts.arg("message").mustBe(stringValue()).asStringVector().mustBe(notEmpty(), RError.Message.INVALID_STRING_IN_WARNING).findFirst();

    }

    @Specialization
    @TruffleBoundary
    protected String warning(boolean call, boolean immediate, boolean noBreakWarning, String message) {
        RErrorHandling.warningcallInternal(call, message, immediate, noBreakWarning);
        return message;
    }
}
