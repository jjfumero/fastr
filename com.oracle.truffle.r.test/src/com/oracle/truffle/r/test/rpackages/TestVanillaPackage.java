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
package com.oracle.truffle.r.test.rpackages;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

/**
 * Tests related to the loading, etc. of R packages.
 */
public class TestVanillaPackage extends TestRPackages {

    private static final String[] TEST_PACKAGES = new String[]{"vanilla"};

    @BeforeClass
    public static void setupInstallMyTestPackages() {
        setupInstallTestPackages(TEST_PACKAGES);
    }

    @AfterClass
    public static void tearDownUninstallMyTestPackages() {
        tearDownUninstallTestPackages(TEST_PACKAGES);
    }

    @Test
    public void testLoadVanilla() {
        assertEval(TestBase.template("{ library(\"vanilla\", lib.loc = \"%0\"); r <- vanilla(); detach(\"package:vanilla\"); r }", new String[]{TestRPackages.libLoc()}));
    }

    @Test
    public void testSimpleFunction() {
        assertEval(TestBase.template("{ library(\"vanilla\", lib.loc = \"%0\"); r <- functionTest(c(1,2,3,4,5,6),8:10); detach(\"package:vanilla\"); r }",
                        new String[]{TestRPackages.libLoc()}));
    }

    @Test
    public void testQualifiedReplacement() {
        assertEval(TestBase.template("{ library(\"vanilla\", lib.loc = \"%0\"); r<-42; vanilla::foo(r)<-7; detach(\"package:vanilla\"); r }", new String[]{TestRPackages.libLoc()}));
    }
}
