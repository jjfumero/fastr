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
package com.oracle.truffle.r.nodes.builtin.base.fastpaths;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;

public abstract class IntersectFastPath extends RFastPathNode {

    protected static final int TYPE_LIMIT = 2;

    private static final int[] EMPTY_INT_ARRAY = new int[0];

    @Specialization(limit = "TYPE_LIMIT", guards = {"x.getLength() > 0", "y.getLength() > 0", "x.getClass() == xClass", "y.getClass() == yClass"})
    protected RAbstractIntVector intersect(RAbstractIntVector x, RAbstractIntVector y,
                    @Cached("x.getClass()") Class<? extends RAbstractIntVector> xClass,
                    @Cached("y.getClass()") Class<? extends RAbstractIntVector> yClass,
                    @Cached("createBinaryProfile()") ConditionProfile isXSortedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile isYSortedProfile,
                    @Cached("createBinaryProfile()") ConditionProfile resultLengthMatchProfile) {
        // apply the type profiles:
        RAbstractIntVector profiledX = xClass.cast(x);
        RAbstractIntVector profiledY = yClass.cast(y);

        int xLength = profiledX.getLength();
        int yLength = profiledY.getLength();
        RBaseNode.reportWork(this, xLength + yLength);

        int count = 0;
        int[] result = EMPTY_INT_ARRAY;
        int maxResultLength = Math.min(xLength, yLength);
        if (isXSortedProfile.profile(isSorted(profiledX))) {
            RAbstractIntVector tempY;
            if (isYSortedProfile.profile(isSorted(profiledY))) {
                tempY = profiledY;
            } else {
                int[] temp = new int[yLength];
                for (int i = 0; i < yLength; i++) {
                    temp[i] = profiledY.getDataAt(i);
                }
                sort(temp);
                tempY = RDataFactory.createIntVector(temp, profiledY.isComplete());
            }
            int xPos = 0;
            int yPos = 0;
            int xValue = profiledX.getDataAt(xPos);
            int yValue = tempY.getDataAt(yPos);
            while (true) {
                if (xValue == yValue) {
                    if (count >= result.length) {
                        result = Arrays.copyOf(result, Math.min(maxResultLength, Math.max(result.length * 2, 8)));
                    }
                    result[count++] = xValue;
                    // advance over similar entries
                    while (true) {
                        if (xPos >= xLength - 1) {
                            break;
                        }
                        int nextValue = profiledX.getDataAt(xPos + 1);
                        if (xValue != nextValue) {
                            break;
                        }
                        xPos++;
                        xValue = nextValue;
                    }
                    if (++xPos >= xLength || ++yPos >= yLength) {
                        break;
                    }
                    xValue = profiledX.getDataAt(xPos);
                    yValue = tempY.getDataAt(yPos);
                } else if (xValue < yValue) {
                    if (++xPos >= xLength) {
                        break;
                    }
                    xValue = profiledX.getDataAt(xPos);
                } else {
                    if (++yPos >= yLength) {
                        break;
                    }
                    yValue = tempY.getDataAt(yPos);
                }
            }
        } else {
            int[] temp = new int[yLength];
            boolean[] used = new boolean[yLength];
            for (int i = 0; i < yLength; i++) {
                temp[i] = profiledY.getDataAt(i);
            }
            sort(temp);

            for (int i = 0; i < xLength; i++) {
                int value = profiledX.getDataAt(i);
                int pos = Arrays.binarySearch(temp, value);
                if (pos >= 0 && !used[pos]) {
                    used[pos] = true;
                    if (count >= result.length) {
                        result = Arrays.copyOf(result, Math.min(maxResultLength, Math.max(result.length * 2, 8)));
                    }
                    result[count++] = value;
                }
            }
        }
        return RDataFactory.createIntVector(resultLengthMatchProfile.profile(count == result.length) ? result : Arrays.copyOf(result, count), profiledX.isComplete() | profiledY.isComplete());
    }

    private static boolean isSorted(RAbstractIntVector vector) {
        int length = vector.getLength();
        int lastValue = vector.getDataAt(0);
        for (int i = 1; i < length; i++) {
            int value = vector.getDataAt(i);
            if (value < lastValue) {
                return false;
            }
            lastValue = value;
        }
        return true;
    }

    @TruffleBoundary
    private static void sort(int[] temp) {
        Arrays.sort(temp);
    }

    @Fallback
    protected Object fallback(@SuppressWarnings("unused") Object x, @SuppressWarnings("unused") Object y) {
        return null;
    }
}
