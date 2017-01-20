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
package com.oracle.truffle.r.nodes.builtin.base.printer;

import java.io.StringWriter;

import com.oracle.truffle.r.runtime.data.RAttributeStorage;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

public class StringPrettyWriter extends StringWriter implements PrettyWriter {

    @Override
    public void begin(Object value) {
    }

    @Override
    public void end(Object value) {
    }

    @Override
    public void beginAttributes(RAttributeStorage value) {
    }

    @Override
    public void endAttributes(RAttributeStorage value) {
    }

    @Override
    public void beginValue(Object value) {
    }

    @Override
    public void endValue(Object value) {
    }

    @Override
    public void beginElement(RAbstractVector vector, int index, FormatMetrics fm) {
    }

    @Override
    public void endElement(RAbstractVector vector, int index, FormatMetrics fm) {
    }

    @Override
    public String getPrintReport() {
        return toString();
    }
}
