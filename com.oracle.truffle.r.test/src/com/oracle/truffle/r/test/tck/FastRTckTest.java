/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.test.tck;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.r.runtime.RSource;
import com.oracle.truffle.tck.TruffleTCK;

public class FastRTckTest extends TruffleTCK {
    @Test
    public void testVerifyPresence() {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        assertTrue("Our language is present", vm.getLanguages().containsKey("text/x-r"));
    }

    // @formatter:off
    private static final Source INITIALIZATION = RSource.fromTextInternal(
        "fourtyTwo <- function() {\n" +
        "  42L\n" +
        "}\n" +
        "plus <- function(a, b) {\n" +
        "  a + b\n" +
        "}\n" +
        "identity <- function(a) {\n" +
        "  a\n" +
        "}\n" +
        "apply <- function(f) {\n" +
        "  f(18L, 32L) + 10L\n" +
        "}\n" +
        "null <- function() {\n" +
        "  NULL\n" +
        "}\n" +
        "counter <- 0L\n" +
        "count <- function() {\n" +
        "  counter <<- counter + 1L\n" +
        "}\n" +
        "complexAdd <- function(a, b) {\n" +
        " a$imaginary <- a$imaginary + b$imaginary\n" +
        " a$real <- a$real + b$real\n" +
        "}\n" +
        "countUpWhile <- function(fn) {\n" +
        " counter <- 0\n" +
        " while (T) {\n" +
        "  if (!fn(counter)) {\n" +
        "   break\n" +
        "  }\n" +
        "  counter <- counter + 1\n" +
        " }\n" +
        "}\n" +
        "complexSumReal <- function(a) {\n" +
        " sum <- 0\n" +
        " for (i in 1:length(a)) {\n" +
        "   sum <- sum + a[i]$real\n" +
        " }\n" +
        " return(sum)\n" +
        "}\n" +
        "complexCopy <- function(a, b) {\n" +
        " for (i in 0:(length(b)-1)) {\n" +
        "   a[i]$real <- b[i]$real\n" +
        "   a[i]$imaginary <- b[i]$imaginary\n" +
        " }\n" +
        "}\n" +
        "valuesObject <- function() {\n" +
        "  list('byteValue'=0L, 'shortValue'=0L, 'intValue'=0L, 'longValue'=0L, 'floatValue'=0, 'doubleValue'=0, 'charValue'=48L, 'stringValue'='', 'booleanValue'=FALSE)\n" +
        "}\n" +
        "addNumbersFunction <- function() {\n" +
        "  function(a, b) a + b\n" +
        "}\n" +
        "objectWithValueProperty <- function() {\n" +
        "  list(value = 42L)\n" +
        "}\n" +
        "callFunction <- function(f) {\n" +
        "  f(41L, 42L)\n" +
        "}\n" +
        "objectWithElement <- function(f) {\n" +
        "  c(0L, 0L, 42L, 0L)\n" +
        "}\n" +
        "objectWithValueAndAddProperty <- function(f) {\n" +
        "  e <- new.env()\n" +
        "  e$value <- 0L\n" +
        "  e$add <- function(inc) { e$value <- e$value + inc; e$value }\n" +
        "  e\n" +
        "}\n" +
        "callMethod <- function(f) {\n" +
        "  f(41L, 42L)\n" +
        "}\n" +
        "readElementFromForeign <- function(f) {\n" +
        "  f[[3L]]\n" +
        "}\n" +
        "writeElementToForeign <- function(f) {\n" +
        "  f[[3L]] <- 42L\n" +
        "}\n" +
        "readValueFromForeign <- function(o) {\n" +
        "  o$value\n" +
        "}\n" +
        "writeValueToForeign <- function(o) {\n" +
        "  o$value <- 42L\n" +
        "}\n" +
        "getSizeOfForeign <- function(o) {\n" +
        "  length(o)\n" +
        "}\n" +
        "isNullOfForeign <- function(o) {\n" +
        "  .fastr.interop.toBoolean(.fastr.interop.isNull(o))\n" +
        "}\n" +
        "hasSizeOfForeign <- function(o) {\n" +
        "  .fastr.interop.toBoolean(.fastr.interop.hasSize(o))\n" +
        "}\n" +
        "isExecutableOfForeign <- function(o) {\n" +
        "  .fastr.interop.toBoolean(.fastr.interop.isExecutable(o))\n" +
        "}\n" +
        "for (name in ls()) .fastr.interop.export(name, get(name))\n",
        RSource.Internal.TCK_INIT
    );
    // @formatter:on

    @Override
    protected PolyglotEngine prepareVM(Builder builder) throws Exception {
        PolyglotEngine vm = builder.build();
        vm.eval(INITIALIZATION);
        return vm;
    }

    @Override
    protected String mimeType() {
        return "text/x-r";
    }

    @Override
    protected String fourtyTwo() {
        return "fourtyTwo";
    }

    @Override
    protected String plusInt() {
        return "plus";
    }

    @Override
    protected String identity() {
        return "identity";
    }

    @Override
    protected String returnsNull() {
        return "null";
    }

    @Override
    protected String applyNumbers() {
        return "apply";
    }

    @Override
    protected String countInvocations() {
        return "count";
    }

    @Override
    protected String complexAdd() {
        return "complexAdd";
    }

    @Override
    protected String complexSumReal() {
        return "complexSumReal";
    }

    @Override
    protected String complexCopy() {
        return "complexCopy";
    }

    @Override
    protected String invalidCode() {
        // @formatter:off
        return
            "main <- function() {\n";
        // @formatter:on
    }

    @Override
    protected String valuesObject() {
        return "valuesObject";
    }

    @Override
    protected String countUpWhile() {
        return "countUpWhile";
    }

    @Override
    protected String addToArray() {
        // TODO not yet supported
        return null;
    }

    @Override
    protected String getSizeOfForeign() {
        return "getSizeOfForeign";
    }

    @Override
    protected String isNullForeign() {
        return "isNullOfForeign";
    }

    @Override
    protected String hasSizeOfForeign() {
        return "hasSizeOfForeign";
    }

    @Override
    protected String isExecutableOfForeign() {
        return "isExecutableOfForeign";
    }

    @Override
    protected String readValueFromForeign() {
        return "readValueFromForeign";
    }

    @Override
    protected String writeValueToForeign() {
        return "writeValueToForeign";
    }

    @Override
    protected String callFunction() {
        return "callFunction";
    }

    @Override
    protected String objectWithElement() {
        return "objectWithElement";
    }

    @Override
    protected String objectWithValueAndAddProperty() {
        return "objectWithValueAndAddProperty";
    }

    @Override
    protected String callMethod() {
        return "callMethod";
    }

    @Override
    protected String readElementFromForeign() {
        return "readElementFromForeign";
    }

    @Override
    protected String writeElementToForeign() {
        return "writeElementToForeign";
    }

    @Override
    protected String objectWithValueProperty() {
        return "objectWithValueProperty";
    }

    @Override
    protected String functionAddNumbers() {
        return "addNumbersFunction";
    }

    @Override
    public void readWriteBooleanValue() throws Exception {
        // TODO not yet supported
    }

    @Override
    public void readWriteCharValue() throws Exception {
        // TODO not yet supported
    }

    @Override
    public void readWriteShortValue() throws Exception {
        // TODO not yet supported
    }

    @Override
    public void readWriteByteValue() throws Exception {
        // TODO not yet supported
    }

    @Override
    public void readWriteFloatValue() throws Exception {
        // TODO not yet supported
    }

    @Override
    public void testAddComplexNumbersWithMethod() throws Exception {
        // TODO not yet supported
    }

    @Override
    @Test
    public void testNull() {
        // disabled because we don't provide a Java "null" value in R
    }

    @Override
    @Test
    public void testNullInCompoundObject() {
        // disabled because we don't provide a Java "null" value in R
    }

    @Override
    @Test
    public void testPlusWithIntsOnCompoundObject() throws Exception {
        // TODO support this test case.
    }

    @Override
    @Test
    public void testMaxOrMinValue() throws Exception {
        // TODO support this test case.
    }

    @Override
    @Test
    public void testMaxOrMinValue2() throws Exception {
        // TODO support this test case.
    }

    @Override
    @Test
    public void testFortyTwoWithCompoundObject() throws Exception {
        // TODO support this test case.
    }

    @Override
    public void testPlusWithFloat() throws Exception {
        // no floats in FastR
    }

    @Override
    public void testPrimitiveReturnTypeFloat() throws Exception {
        // no floats in FastR
    }

    @Override
    public void testPlusWithOneNegativeShort() throws Exception {
        // no floats in FastR
    }

    @Override
    public void testPlusWithDoubleFloatSameAsInt() throws Exception {
        // no floats in FastR
    }

    @Override
    public void testPlusWithLongMaxIntMinInt() throws Exception {
        // no longs in FastR
    }

    @Override
    public void testPlusWithLong() throws Exception {
        // no longs in FastR
    }

    @Override
    public void testPrimitiveReturnTypeLong() throws Exception {
        // no longs in FastR
    }

    @Override
    public void testPlusWithBytes() throws Exception {
        // no bytes in FastR
    }

    @Override
    public void testPlusWithOneNegativeByte() throws Exception {
        // no bytes in FastR
    }

    @Override
    public void testPlusWithShort() throws Exception {
        // no shorts in FastR
    }

    @Override
    public void testPrimitiveReturnTypeShort() throws Exception {
        // no shorts in FastR
    }

    @Override
    public void testGlobalObjectIsAccessible() throws Exception {
        // no global object in fastr.
    }

    @Override
    public void testNullCanBeCastToAnything() throws Exception {
        // TODO support
    }

    @Override
    public void multiplyTwoVariables() throws Exception {
        // TODO support
    }

    @Override
    public void testEvaluateSource() throws Exception {
        // TODO support
    }

    @Override
    public void testCopyComplexNumbersA() {
        // TODO determine the semantics of assignments to a[i]$b
    }

    @Override
    public void testCopyComplexNumbersB() {
        // TODO determine the semantics of assignments to a[i]$b
    }

    @Override
    public void testCopyStructuredComplexToComplexNumbersA() {
        // TODO determine the semantics of assignments to a[i]$b
    }

    @Override
    public void testAddComplexNumbers() {
        // TODO determine the semantics of assignments to a[i]$b
    }

    @Override
    public void testWriteToObjectWithElement() throws Exception {
        // TODO mismatch between mutable and immutable data types
    }

    @Override
    public void testObjectWithValueAndAddProperty() throws Exception {
        // TODO mismatch between mutable and immutable data types
    }

    @Override
    public void testCallMethod() throws Exception {
        // R does not have method calls
    }

    @Override
    public String multiplyCode(String firstName, String secondName) {
        return firstName + '*' + secondName;
    }

    @Override
    protected String[] metaObjects() {
        return null; // TBD add proper impl here.
    }

    @Override
    protected String valueWithSource() {
        return null; // TBD add proper impl here.
    }

}
