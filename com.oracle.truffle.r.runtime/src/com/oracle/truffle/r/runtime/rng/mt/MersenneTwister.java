/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1997-2002, Makoto Matsumoto and Takuji Nishimura
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */

/*
 This is a Java reimplementation of MT19937, derived by translation from
 the C implementation, which is available from
 http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/MT2002/CODES/mt19937ar.c
 and is copyright (c) 1997-2002, Makoto Matsumoto and Takuji Nishimura,
 and also from the GNU R file RNG.c

 The original header from the C implementation by Matsumoto and Nishimura is included below.
 */

/*
 A C-program for MT19937, with initialization improved 2002/1/26.
 Coded by Takuji Nishimura and Makoto Matsumoto.

 Before using, initialize the state by using init_genrand(seed)
 or init_by_array(init_key, key_length).

 Copyright (C) 1997 - 2002, Makoto Matsumoto and Takuji Nishimura,
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.

 3. The names of its contributors may not be used to endorse or promote
 products derived from this software without specific prior written
 permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


 Any feedback is very welcome.
 http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html
 email: m-mat @ math.sci.hiroshima-u.ac.jp (remove space)
 */
package com.oracle.truffle.r.runtime.rng.mt;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.r.runtime.rng.RNGInitAdapter;
import com.oracle.truffle.r.runtime.rng.RRNG;
import com.oracle.truffle.r.runtime.rng.RRNG.Kind;

public final class MersenneTwister extends RNGInitAdapter {

    /* Period parameters */
    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIXA = 0x9908b0df; /* constant vector a */
    private static final int UPPERMASK = 0x80000000; /* most significant w-r bits */
    private static final int LOWERMASK = 0x7fffffff; /* least significant r bits */
    private static final int TEMPERING_MASK_B = 0x9d2c5680;
    private static final int TEMPERING_MASK_C = 0xefc60000;

    /*
     * This generator can efficiently generate many random numbers in one go, upon each call to
     * genrandDouble() we take the next random value from 'buffer'. If the buffer is empty, we fill
     * it.
     */
    private static final int BUFFER_SIZE = N;
    private final double[] buffer = new double[BUFFER_SIZE];
    private int bufferIndex = BUFFER_SIZE;

    /**
     * The array for the state vector. In GnuR, the state array is common to all algorithms (named
     * {@code dummy}), and the zero'th element is the number of seeds, but the algorithm uses
     * pointer arithmetic to set {@code mt} to {@code dummy + 1}.
     */
    private int getMt(int i) {
        return getISeedItem(i + 1);
    }

    private void setMt(int i, int val) {
        setISeedItem(i + 1, val);
    }

    @Override
    public void setISeed(int[] seeds) {
        fixupSeeds(false);
        // kill the current buffer if the seed changes
        bufferIndex = BUFFER_SIZE;
    }

    /**
     * This function is derived from GNU R, RNG.c (RNG_Init). N.B. GnuR generates N+1 seeds in
     * {@code dummy} and then overwrites the zeroth element with N, which is evidently important for
     * "historical compatibility". Since we can't play pointer arithmetic games, we generate but do
     * not store the seed in {@code mt[0]}. To compare, call set.seed(4357) followed by .Random.seed
     * in GnuR and FastR. The values in the vector should be the same.
     *
     */
    @Override
    @TruffleBoundary
    public void init(int seedParam) {
        int seed = seedParam;
        for (int i = 0; i < getNSeed(); i++) {
            seed = (69069 * seed + 1);
            setISeedItem(i, seed);
        }
        fixupSeeds(true);
        bufferIndex = BUFFER_SIZE;
    }

    @Override
    @TruffleBoundary
    public void fixupSeeds(boolean initial) {
        if (initial) {
            setISeedItem(0, N);
        }
        if (getISeedItem(0) <= 0) {
            setISeedItem(0, N);
        }
        boolean notAllZero = false;
        for (int i = 1; i <= N; i++) {
            if (getISeedItem(i) != 0) {
                notAllZero = true;
            }
        }
        if (!notAllZero) {
            init(RRNG.timeToSeed());
        }
    }

    /**
     * The actual generating method, essentially transcribed from MT_genrand in GnuR RNG.c.
     */
    @Override
    public double genrandDouble() {
        if (bufferIndex == BUFFER_SIZE) {
            int localDummy0 = getISeedItem(0);
            int localMti = localDummy0;
            // It appears that this never happens
            // sgenrand(4357);
            assert localMti != N + 1;
            int pos = 0;
            while (true) {
                int loopCount = Math.min(BUFFER_SIZE - pos, N - localMti);
                for (int i = 0; i < loopCount; i++) {
                    int y = getMt(localMti + i);
                    /* Tempering */
                    y ^= (y >>> 11);
                    y ^= (y << 7) & TEMPERING_MASK_B;
                    y ^= (y << 15) & TEMPERING_MASK_C;
                    y ^= (y >>> 18);
                    buffer[pos + i] = ((y + Integer.MIN_VALUE) - (double) Integer.MIN_VALUE) * I2_32M1;
                }
                for (int i = 0; i < loopCount; i++) {
                    buffer[pos + i] = fixup(buffer[pos + i]);
                }
                localMti += loopCount;
                pos += loopCount;

                if (pos == BUFFER_SIZE) {
                    break;
                }
                /* generate N words at one time */
                int kk;
                for (kk = 0; kk < N - M; kk++) {
                    int y2y = (getMt(kk) & UPPERMASK) | (getMt(kk + 1) & LOWERMASK);
                    setMt(kk, getMt(kk + M) ^ (y2y >>> 1) ^ mag01(y2y & 0x1));
                }
                for (; kk < N - 1; kk++) {
                    int y2y = (getMt(kk) & UPPERMASK) | (getMt(kk + 1) & LOWERMASK);
                    setMt(kk, getMt(kk + (M - N)) ^ (y2y >>> 1) ^ mag01(y2y & 0x1));
                }
                int y2y = (getMt(N - 1) & UPPERMASK) | (getMt(0) & LOWERMASK);
                setMt(N - 1, getMt(M - 1) ^ (y2y >>> 1) ^ mag01(y2y & 0x1));

                localMti = 0;
            }
            localDummy0 = localMti;
            setISeedItem(0, localDummy0);
            bufferIndex = 0;
        }
        return buffer[bufferIndex++];
    }

    private static int mag01(int v) {
        return (v & 1) != 0 ? MATRIXA : 0;
    }

    @Override
    public Kind getKind() {
        return Kind.MERSENNE_TWISTER;
    }

    @Override
    public int getNSeed() {
        return 1 + N;
    }

}
