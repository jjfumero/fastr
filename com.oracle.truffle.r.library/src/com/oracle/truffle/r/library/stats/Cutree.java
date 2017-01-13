/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995--2015, The R Core Team
 * Copyright (c) 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.library.stats;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetDimAttributeNode;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RExternalBuiltinNode;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;

// translated from library/stats/src/hclust_utils.c

public abstract class Cutree extends RExternalBuiltinNode.Arg2 {

    @Override
    public void createCasts(CastBuilder casts) {
        casts.arg(0).asIntegerVector();
        casts.arg(1).asIntegerVector();
    }

    @Specialization
    protected RIntVector cutree(RAbstractIntVector mergeIn, RAbstractIntVector whichIn, @Cached("create()") GetDimAttributeNode getDimNode) {
        RIntVector merge = mergeIn.materialize();
        RIntVector which = whichIn.materialize();
        int whichLen = which.getLength();

        int j;
        int k;
        int l;
        int nclust;
        int m1;
        int m2;
        int mm = 0;
        boolean foundJ;

        int n = getDimNode.nrows(merge) + 1;
        /*
         * The C code uses 1-based indices for the next three arrays and so set the int * value
         * behind the actual start of the array. To keep the logic equivalent, we call adj(k) on the
         * index, except for the simple init loops.
         */
        boolean[] sing = new boolean[n];
        int[] mNr = new int[n];
        int[] z = new int[n];

        int[] iAns = new int[n * whichLen];
        int[] iMerge = merge.getDataWithoutCopying();
        int[] iWhich = which.getDataWithoutCopying();

        // for (k = 1; k <= n; k++) {
        for (k = 0; k < n; k++) {
            sing[k] = true; /* is k-th obs. still alone in cluster ? */
            mNr[k] = 0; /* containing last merge-step number of k-th obs. */
        }

        for (k = 1; k <= n - 1; k++) {
            /* k-th merge, from n-k+1 to n-k atoms: (m1,m2) = merge[ k , ] */
            m1 = iMerge[k - 1];
            m2 = iMerge[n - 1 + k - 1];

            if (m1 < 0 && m2 < 0) { /* merging atoms [-m1] and [-m2] */
                mNr[adj(-m1)] = mNr[adj(-m2)] = k;
                sing[adj(-m1)] = sing[adj(-m2)] = false;
            } else if (m1 < 0 || m2 < 0) { /* the other >= 0 */
                if (m1 < 0) {
                    j = -m1;
                    m1 = m2;
                } else {
                    j = -m2;
                }
                /* merging atom j & cluster m1 */
                for (l = 1; l <= n; l++) {
                    if (mNr[adj(l)] == m1) {
                        mNr[adj(l)] = k;
                    }
                }
                mNr[adj(j)] = k;
                sing[adj(j)] = false;
            } else { /* both m1, m2 >= 0 */
                for (l = 1; l <= n; l++) {
                    if (mNr[adj(l)] == m1 || mNr[adj(l)] == m2) {
                        mNr[adj(l)] = k;
                    }
                }
            }

            /*
             * does this k-th merge belong to a desired group size which[j] ? if yes, find j (maybe
             * multiple ones):
             */
            foundJ = false;
            for (j = 0; j < whichLen; j++) {
                if (iWhich[j] == n - k) {
                    if (!foundJ) { /* first match (and usually only one) */
                        foundJ = true;
                        // for (l = 1; l <= n; l++)
                        for (l = 0; l < n; l++) {
                            z[l] = 0;
                        }
                        nclust = 0;
                        mm = j * n; /* may want to copy this column of ans[] */
                        for (l = 1, m1 = mm; l <= n; l++, m1++) {
                            if (sing[adj(l)]) {
                                iAns[m1] = ++nclust;
                            } else {
                                if (z[adj(mNr[adj(l)])] == 0) {
                                    z[adj(mNr[adj(l)])] = ++nclust;
                                }
                                iAns[m1] = z[adj(mNr[adj(l)])];
                            }
                        }
                    } else { /* found_j: another which[j] == n-k : copy column */
                        for (l = 1, m1 = j * n, m2 = mm; l <= n; l++, m1++, m2++) {
                            iAns[m1] = iAns[m2];
                        }
                    }
                } /* if ( match ) */
            } /* for(j .. which[j] ) */
        } /* for(k ..) {merge} */

        /* Dealing with trivial case which[] = n : */
        for (j = 0; j < whichLen; j++) {
            if (iWhich[j] == n) {
                for (l = 1, m1 = j * n; l <= n; l++, m1++) {
                    iAns[m1] = l;
                }
            }
        }

        RIntVector result = RDataFactory.createIntVector(iAns, RDataFactory.COMPLETE_VECTOR, new int[]{n, whichLen});
        return result;

    }

    private static int adj(int i) {
        return i - 1;
    }
}
