/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_sample2 extends TestBase {
    @Test
    public void testSample2() {
        assertEval("set.seed(42);  .Internal(sample2(10, 2))");
        assertEval("set.seed(42);  .Internal(sample2(10L, 3L))");
        assertEval("set.seed(42);  x <- .Internal(sample2(10L, 3L)); y <- .Internal(sample2(10L, 3L)); list(x, y); ");
        // test with n > MAX_INT
        assertEval("set.seed(42);  .Internal(sample2(4147483647, 10))");
    }

    @Test
    public void testArgsCasts() {
        assertEval("set.seed(42); .Internal(sample2(-2, 1))");
        assertEval("set.seed(42); .Internal(sample2(-2L, 1))");
        assertEval("set.seed(42); .Internal(sample2(NA, 1))");
        assertEval("set.seed(42); .Internal(sample2(NaN, 1))");

        assertEval("set.seed(42); .Internal(sample2(10, 8))");
        assertEval("set.seed(42); .Internal(sample2(10, -2))");
        assertEval("set.seed(42); .Internal(sample2(10, 2.99))");
    }
}
