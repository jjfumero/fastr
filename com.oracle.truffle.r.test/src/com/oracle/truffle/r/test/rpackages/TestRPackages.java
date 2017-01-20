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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;

import com.oracle.truffle.r.runtime.REnvVars;
import com.oracle.truffle.r.runtime.RVersionNumber;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.test.TestBase;

/**
 * Tests related to the loading, etc. of R packages.
 *
 * THis class should be subclassed by a test that wishes to install one or more packages and
 * possibly run additional tests after installation using a pattern of the form
 * {@code library(pkg, lib.loc="%0"); sometest()}. Note the use of the {@code %0}, which must be
 * satisfied by passing the value of {@code libLoc()}. This is required because the test VM is not
 * aware of the test install location so it must be explicitly specified. The use of the {@code %0}
 * parameter mechanism also requires the use of {@link TestBase#template} in the test itself.
 *
 * A subclass must provide {@code @BeforeClass} and {@code @AfterClass}methods that call
 * {@link #setupInstallTestPackages} and {@link #tearDownUninstallTestPackages}, respectively, to
 * install/remove the specific set of packages relevant to the test.
 *
 * N.B. The same directory is used when generating expected output with GnuR, and running FastR, to
 * keep the {@code lib_loc} argument the same in the test string. So the install is destructive, but
 * ok as there is never a clash.
 *
 * The install directory is cleaned on every call to {@link #setupInstallTestPackages} in case a
 * previous install failed to complete {@link #tearDownUninstallTestPackages} successfully.
 */
public abstract class TestRPackages extends TestBase {

    private static final String SYSTEM2_COMMAND = "system2('%s', c('CMD', 'INSTALL', '%s'), env='R_LIBS=%s', stdout=T, stderr=T)";

    private static final class PackagePath {
        /**
         * The path containing the package distributions as tar files.
         */
        private final Path path;

        private PackagePath(Path path) {
            this.path = path;
        }
    }

    /**
     * The path to the install directory. This is fixed across all tests.
     */
    private static Path installDir;

    /**
     * Map from package name to info on its location.
     */
    private static final Map<String, PackagePath> packageMap = new HashMap<>();

    private static Path installDir() {
        if (installDir == null) {
            installDir = TestBase.createTestDir("com.oracle.truffle.r.test.rpackages");
        }
        return installDir;
    }

    protected static String libLoc() {
        return installDir().toString();
    }

    private static boolean uninstallPackage(String packageName) {
        Path packageDir = installDir().resolve(packageName);
        try {
            deleteDir(packageDir);
        } catch (Throwable e) {
            return false;
        }
        return true;
    }

    /**
     * Pass a custom subclass of this class to override the actual location of the package tar file.
     */
    protected static class Resolver {
        Path getPath(String p) {
            return testNativePath().resolve(p).resolve("lib").resolve(p + ".tar");
        }
    }

    private static Path testNativePath() {
        Path p = TestBase.getNativeProjectFile(Paths.get("packages"));
        return p;
    }

    private static PackagePath getPackagePaths(String pkg, Path path) {
        PackagePath result = packageMap.get(pkg);
        if (result == null) {
            result = new PackagePath(path);
            packageMap.put(pkg, result);
        }
        return result;
    }

    protected static void setupInstallTestPackages(String[] testPackages) {
        setupInstallTestPackages(testPackages, new Resolver());
    }

    private static boolean installPackage(PackagePath packagePath) {
        String cmd;
        Path binBase;
        if (generatingExpected()) {
            // use GnuR (internal)
            binBase = Paths.get(REnvVars.rHome(), "com.oracle.truffle.r.native", "gnur", RVersionNumber.R_HYPHEN_FULL);
        } else {
            // use FastR
            binBase = Paths.get(REnvVars.rHome());
        }
        cmd = binBase.resolve("bin").resolve("R").toString();
        try {
            Object result = evalInstallPackage(String.format(SYSTEM2_COMMAND, cmd, packagePath.path.toString(), installDir().toString()));
            boolean success;
            if (generatingExpected()) {
                String stringResult = (String) result;
                success = stringResult.contains("* DONE (");
                if (!success) {
                    System.out.println(stringResult);
                }
            } else {
                RStringVector vecResult = (RStringVector) result;
                success = vecResult.getAttr("status") == null;
                if (!success) {
                    String[] output = vecResult.getDataWithoutCopying();
                    for (String line : output) {
                        System.out.println(line);
                    }
                }
            }
            return success;
        } catch (Throwable t) {
            return false;
        }
    }

    protected static void setupInstallTestPackages(String[] testPackages, Resolver resolver) {
        if (!checkOnly()) {
            TestBase.deleteDir(installDir());
            installDir().toFile().mkdirs();
            System.out.printf(".begin install.");
            for (String p : testPackages) {
                // Takes time, provide user feedback
                System.out.printf(".pkg: %s.", p);
                PackagePath packagePath = getPackagePaths(p, resolver.getPath(p));
                if (!installPackage(packagePath)) {
                    Assert.fail(String.format("package %s failed to install", p));
                }
            }
            System.out.printf(".end install.");
        }
    }

    protected static void tearDownUninstallTestPackages(String[] testPackages) {
        if (!checkOnly()) {
            for (String p : testPackages) {
                if (!uninstallPackage(p)) {
                    System.err.println("WARNING: error deleting package: " + p);
                }
            }
        }
    }
}
