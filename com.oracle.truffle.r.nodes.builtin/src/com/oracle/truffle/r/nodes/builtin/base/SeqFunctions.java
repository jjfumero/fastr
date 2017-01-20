/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1998  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1998-2015,  The R Core Team
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.gte;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.size;
import static com.oracle.truffle.r.runtime.RDispatch.INTERNAL_GENERIC;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetClassAttributeNode;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.SeqFunctionsFactory.IsMissingOrNumericNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.SeqFunctionsFactory.SeqIntNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.SeqFunctionsFactory.SeqIntNodeGen.GetIntegralNumericNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.SeqFunctionsFactory.SeqIntNodeGen.IsIntegralNumericNodeGen;
import com.oracle.truffle.r.nodes.control.RLengthNode;
import com.oracle.truffle.r.nodes.control.RLengthNodeGen;
import com.oracle.truffle.r.nodes.ffi.AsRealNode;
import com.oracle.truffle.r.nodes.ffi.AsRealNodeGen;
import com.oracle.truffle.r.nodes.function.CallMatcherNode.CallMatcherGenericNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RArgsValuesAndNames;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RDoubleSequence;
import com.oracle.truffle.r.runtime.data.REmpty;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RIntSequence;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.RMissing;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RSequence;
import com.oracle.truffle.r.runtime.data.RTypesFlatLayout;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RFastPathNode;

/**
 * Sequence builtins, {@code seq_along}, {@code seq_len}, {@code seq.int} and fast paths for
 * {@code seq} and {@code seq.default}.
 *
 * Why the fast paths for {@code seq} and {@code seq.default}?. Despite the provision of the more
 * efficient builtins, and encouragement to use them in when appropriate in the R documentation, it
 * seems that many programmers do not heed this advice. Since {@code seq} is generic and the default
 * method {@code seq.default} is coded in R, this can cause a considerable reduction in performance,
 * which is more noticeable in FastR than GNU R.
 *
 * Superficially {@code seq.default} appears to be an R translation of the C code in {@code seq.int}
 * (or vice-versa). This appears to be true for numeric types, but there are some differences. E.g.,
 * {@code seq.int} coerces a character string whereas {@code seq.default} reports an error. Owing to
 * these differences the fast paths do not routinely redirect to {@code seq.int}, only for cases
 * where the arguments are numeric (which is really what we care about anyway for performance).
 * There are also some slight differences in behavior for numeric arguments that may be fixed in an
 * upcoming GNU R release. Currently these are handled by passing a flag when creating the
 * {@link SeqInt} node for the fast paths.
 *
 */
public class SeqFunctions {

    public abstract static class FastPathAdapter extends RFastPathNode {
        public static IsMissingOrNumericNode createIsMissingOrNumericNode() {
            return IsMissingOrNumericNodeGen.create();
        }
    }

    @TypeSystemReference(RTypesFlatLayout.class)
    @SuppressWarnings("unused")
    public abstract static class IsMissingOrNumericNode extends Node {
        public abstract boolean execute(Object obj);

        @Specialization
        protected boolean isMissingOrNumericNode(RMissing obj) {
            return true;
        }

        @Specialization
        protected boolean isMissingOrNumericNode(Integer obj) {
            return true;
        }

        @Specialization
        protected boolean isMissingOrNumericNode(Double obj) {
            return true;
        }

        @Specialization
        protected boolean isMissingOrNumericNode(RAbstractIntVector obj) {
            return true;
        }

        @Specialization
        protected boolean isMissingOrNumericNode(RAbstractDoubleVector obj) {
            return true;
        }

        @Fallback
        protected boolean isMissingOrNumericNode(Object obj) {
            return false;
        }
    }

    @TypeSystemReference(RTypesFlatLayout.class)
    public abstract static class SeqFastPath extends FastPathAdapter {
        @Specialization(guards = {"!hasClass(args, getClassAttributeNode)"})
        @SuppressWarnings("unused")
        protected Object seqNoClassAndNumeric(VirtualFrame frame, RArgsValuesAndNames args,
                        @Cached("createSeqIntForFastPath()") SeqInt seqInt,
                        @Cached("lookupSeqInt()") RFunction seqIntFunction,
                        @Cached("createBinaryProfile()") ConditionProfile isNumericProfile,
                        @Cached("createGetClassAttributeNode()") GetClassAttributeNode getClassAttributeNode,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode fromCheck,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode toCheck,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode byCheck) {
            Object[] rargs = reorderedArguments(args, seqIntFunction);
            if (isNumericProfile.profile(fromCheck.execute(rargs[0]) && toCheck.execute(rargs[1]) && toCheck.execute(rargs[2]))) {
                return seqInt.execute(frame, rargs[0], rargs[1], rargs[2], rargs[3], rargs[4]);
            } else {
                return null;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object seqFallback(VirtualFrame frame, Object args) {
            return null;
        }

        public static RFunction lookupSeqInt() {
            return RContext.lookupBuiltin("seq.int");
        }

        public static GetClassAttributeNode createGetClassAttributeNode() {
            return GetClassAttributeNode.create();
        }

        /**
         * The arguments are reordered if any are named, and later will be checked for missing or
         * numeric.
         */
        public static Object[] reorderedArguments(RArgsValuesAndNames argsIn, RFunction seqIntFunction) {
            RArgsValuesAndNames args = argsIn;
            if (args.getSignature().getNonNullCount() != 0) {
                return CallMatcherGenericNode.reorderArguments(args.getArguments(), seqIntFunction, args.getSignature(), RError.NO_CALLER).getArguments();
            } else {
                int len = argsIn.getLength();
                Object[] xArgs = new Object[5];
                for (int i = 0; i < xArgs.length; i++) {
                    xArgs[i] = i < len ? argsIn.getArgument(i) : RMissing.instance;
                }
                return xArgs;
            }
        }

        /**
         * This guard checks whether the first argument (before reordering) has a class (as it might
         * have an S3 {@code seq} method).
         */
        public boolean hasClass(RArgsValuesAndNames args, GetClassAttributeNode getClassAttributeNode) {
            if (args.getLength() > 0) {
                Object arg = args.getArgument(0);
                if (arg instanceof RAbstractVector && getClassAttributeNode.execute(arg) != null) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Essentially the same as {@link SeqFastPath} but since the signature is explicit there is no
     * need to reorder arguments.
     */
    @TypeSystemReference(RTypesFlatLayout.class)
    public abstract static class SeqDefaultFastPath extends FastPathAdapter {
        @SuppressWarnings("unused")
        @Specialization(guards = {"fromCheck.execute(fromObj)", "toCheck.execute(toObj)", "byCheck.execute(byObj)"})
        protected Object seqDefaultNumeric(VirtualFrame frame, Object fromObj, Object toObj, Object byObj, Object lengthOut, Object alongWith,
                        @Cached("createSeqIntForFastPath()") SeqInt seqInt,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode fromCheck,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode toCheck,
                        @Cached("createIsMissingOrNumericNode()") IsMissingOrNumericNode byCheck) {
            return seqInt.execute(frame, fromObj, toObj, byObj, lengthOut, alongWith);
        }

        /**
         * For everything else (not performance-centric) we invoke the original R code.
         */
        @SuppressWarnings("unused")
        @Fallback
        protected Object seqDefaultFallback(VirtualFrame frame, Object fromObj, Object toObj, Object byObj, Object lengthOut, Object alongWith) {
            return null;
        }
    }

    @TypeSystemReference(RTypesFlatLayout.class)
    @RBuiltin(name = "seq_along", kind = PRIMITIVE, parameterNames = {"along.with"}, behavior = PURE)
    public abstract static class SeqAlong extends RBuiltinNode {

        @Child private RLengthNode length = RLengthNodeGen.create();

        @Specialization
        protected RIntSequence seq(VirtualFrame frame, Object value) {
            return RDataFactory.createIntSequence(1, 1, length.executeInteger(frame, value));
        }
    }

    @TypeSystemReference(RTypesFlatLayout.class)
    @RBuiltin(name = "seq_len", kind = PRIMITIVE, parameterNames = {"length.out"}, behavior = PURE)
    public abstract static class SeqLen extends RBuiltinNode {

        @Override
        protected void createCasts(CastBuilder casts) {
            /*
             * This is slightly different than what GNU R does as it will report coercion warning
             * for: seq_len(c("7", "b")) GNU R (presumably) gets the first element before doing a
             * coercion but I don't think we can do it with our API
             */
            casts.arg("length.out").asIntegerVector().shouldBe(size(1).or(size(0)), RError.Message.FIRST_ELEMENT_USED, "length.out").findFirst(RRuntime.INT_NA,
                            RError.Message.FIRST_ELEMENT_USED, "length.out").mustBe(gte(0), RError.Message.MUST_BE_COERCIBLE_INTEGER);
        }

        @Specialization
        protected RIntSequence seqLen(int length) {
            return RDataFactory.createIntSequence(1, 1, length);
        }
    }

    /**
     * The GNU R logic for this builtin is a complex sequence (sic) of "if" statements, that handle
     * the presence/absence of the arguments. Converting this to Truffle, where we want to tease out
     * specific argument combinations for efficiency is not straightforward and arguably is less
     * transparent.
     *
     * The fact that any of the arguments can be missing is a complicating factor. There is no FastR
     * type that signifies "any type except RMissing", so we have to use guards. We also have to be
     * careful that specializations do not overlap due to the possibility of a missing value.
     *
     * Converted from GNU R src/main/seq.c
     *
     * The specializations are broken into five groups, corresponding to the five "forms" described
     * in <a href="https://stat.ethz.ch/R-manual/R-devel/library/base/html/seq.html">Sequence
     * Generation</a>, (but in a different order).
     *
     * N.B. javac gives error "cannot find symbol" on plain "@RBuiltin".
     */
    @TypeSystemReference(RTypesFlatLayout.class)
    @ImportStatic(AsRealNodeGen.class)
    @com.oracle.truffle.r.runtime.builtins.RBuiltin(name = "seq.int", kind = PRIMITIVE, parameterNames = {"from", "to", "by", "length.out", "along.with",
                    "..."}, dispatch = INTERNAL_GENERIC, genericName = "seq", behavior = PURE)
    @SuppressWarnings("unused")
    public abstract static class SeqInt extends RBuiltinNode {
        private final BranchProfile error = BranchProfile.create();
        private final boolean seqFastPath;

        /**
         * Used by {@link #getLength} guard. It would be good to cache this in the relevant
         * specializations but it does not use {@link RTypesFlatLayout} and that causes an
         * IllegalStateException (no parent).
         */
        @Child private RLengthNode lengthNode = RLengthNode.create();

        private static final double FLT_EPSILON = 1.19209290e-7;

        protected abstract Object execute(VirtualFrame frame, Object start, Object to, Object by, Object lengthOut, Object alongWith);

        protected SeqInt(boolean seqFastPath) {
            this.seqFastPath = seqFastPath;
        }

        protected SeqInt() {
            this(false);
        }

        public static SeqInt createSeqInt() {
            return SeqIntNodeGen.create(false);
        }

        public static SeqInt createSeqIntForFastPath() {
            return SeqIntNodeGen.create(true);
        }

        // No matching args (special case)

        @Specialization
        protected RIntSequence allMissing(RMissing from, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            // GNU R allows this and returns 1
            return RDataFactory.createIntSequence(1, 1, 1);
        }

        /*
         * seq(from) One "from" arg: THE most common case? ASSERT: this handles ALL the cases where
         * "from" is not missing, i.e. the "One" case. Therefore, in subsequent specializations we
         * should be careful about an overlap where "from" might or might not be missing.
         */

        /**
         * Irrespective of the R type, if the length is zero the result is an empty sequence.
         */
        @Specialization(guards = {"!isMissing(from)", "getLength(frame, from) == 0"})
        protected RIntVector emptySeqFromOneArg(VirtualFrame frame, Object from, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            return RDataFactory.createEmptyIntVector();
        }

        /**
         * Also, irrespective of the R type, if the length is greater than 1, the length itself is
         * used as the upper bound of the sequence. This is slightly counter-intuitive as most
         * builtins take the </i>value</i> of the first element and warn about ignoring the rest,
         * but the value likely could not be coerced.
         */
        @Specialization(guards = {"!isMissing(from)", "getLength(frame, from) > 1"})
        protected RIntSequence lenSeqFromOneArg(VirtualFrame frame, Object from, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            return RDataFactory.createIntSequence(1, 1, getLength(frame, from));
        }

        /**
         * A length-1 REAL. Return "1:(int) from" (N.B. from may be negative) EXCEPT
         * {@code seq(0.2)} is NOT the same as {@code seq(0.0)} (according to GNU R)
         */
        @Specialization(guards = "fromVec.getLength() == 1")
        protected RAbstractVector seqFromOneArgDouble(RAbstractDoubleVector fromVec, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            double from = validateDoubleParam(fromVec.getDataAt(0), fromVec, "from");
            int len = effectiveLength(1, from);
            return RDataFactory.createIntSequence(1, from > 0 ? 1 : -1, len);
        }

        /**
         * A length-1 INT. Return "1:from" (N.B. from may be negative)
         */
        @Specialization(guards = "fromVec.getLength() == 1")
        protected RIntSequence seqFromOneArgInt(RAbstractIntVector fromVec, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            int from = validateIntParam(fromVec.getDataAt(0), "from");
            int len = from > 0 ? from : 2 - from;
            return RDataFactory.createIntSequence(1, from > 0 ? 1 : -1, len);
        }

        /**
         * A length-1 something other than REAL/INT. Again, use the length, not the value (which
         * likely would not make sense, e.g. {@code expression(x, y)}). N.B. Without
         * {@code !isNumeric(from)} guard this would "contain" the previous two specializations,
         * which would be incorrect as the result is different.
         */
        @Specialization(guards = {"!isMissing(from)", "getLength(frame, from) == 1", "!isNumeric(from)"})
        protected RIntSequence seqFromOneArgObj(VirtualFrame frame, Object from, RMissing to, RMissing by, RMissing lengthOut, RMissing alongWith) {
            return RDataFactory.createIntSequence(1, 1, 1);
        }

        /**
         * Treat {@code lengthOut==NULL} as {@link RMissing}.
         */
        @Specialization
        protected RAbstractVector seqLengthByMissing(VirtualFrame frame, Object from, Object to, Object by, RNull lengthOut, RMissing alongWith,
                        @Cached("createSeqInt()") SeqInt seqIntNodeRecursive) {
            return (RAbstractVector) seqIntNodeRecursive.execute(frame, from, to, by, RMissing.instance, alongWith);
        }

        /*
         * seq(from,to) but either could be missing. "along.with" is missing and "length.out" is
         * missing (or NULL), and "by" (by) is missing. N.B. we are only interested in the cases
         * "from=missing, to!=missing" and "from!=missing, to!=missing" as
         * "from!=missing, to=missing" is already covered in the "One" specializations.
         *
         * The first two specializations handle the expected common cases with valid arguments. The
         * third specialization handles other types and invalid arguments.
         */

        @Specialization(guards = "validDoubleParams(fromVec, toVec)")
        protected RAbstractVector seqLengthByMissingDouble(VirtualFrame frame, RAbstractDoubleVector fromVec, RAbstractDoubleVector toVec, RMissing by, RMissing lengthOut, RMissing alongWith,
                        @Cached("createBinaryProfile()") ConditionProfile directionProfile) {
            double from = fromVec.getDataAt(0);
            double to = toVec.getDataAt(0);
            RAbstractVector result = createRSequence(from, to, directionProfile);
            return result;
        }

        @Specialization(guards = "validIntParams(fromVec, toVec)")
        protected RAbstractVector seqLengthByMissingInt(VirtualFrame frame, RAbstractIntVector fromVec, RAbstractIntVector toVec, RMissing by, RMissing lengthOut, RMissing alongWith,
                        @Cached("createBinaryProfile()") ConditionProfile directionProfile) {
            int from = fromVec.getDataAt(0);
            int to = toVec.getDataAt(0);
            RIntSequence result = createRIntSequence(from, to, directionProfile);
            return result;
        }

        /**
         * The performance of this specialization, we assert, is not important. It captures a
         * mixture of coercions from improbable types and error cases. N.B. However, mixing doubles
         * and ints <b<will</b> hit this specialization; is that likely and a concern? If
         * "from ==missing", it defaults to 1.0. "to" cannot be missing as that would overlap with
         * previous specializations.
         */
        @Specialization(guards = {"!isMissing(toObj)"})
        protected RAbstractVector seqLengthByMissing(VirtualFrame frame, Object fromObj, Object toObj, RMissing by, RMissing lengthOut, RMissing alongWith,
                        @Cached("create()") AsRealNode asRealFrom, @Cached("create()") AsRealNode asRealTo, @Cached("createBinaryProfile()") ConditionProfile directionProfile) {
            double from;
            if (isMissing(fromObj)) {
                from = 1.0;
            } else {
                validateLength(frame, fromObj, "from");
                from = asRealFrom.execute(fromObj);
                validateDoubleParam(from, fromObj, "from");
            }
            validateLength(frame, toObj, "to");
            double to = asRealTo.execute(toObj);
            validateDoubleParam(to, toObj, "to");
            RAbstractVector result = createRSequence(from, to, directionProfile);
            return result;
        }

        /*
         * seq(from, to, by=). As above but with "by" not missing. Except for the special case of
         * from/to/by all ints, we do not specialize on "by". Again, "from != missing" is already
         * handled in the "One" specializations.
         */

        @Specialization(guards = {"validDoubleParams(fromVec, toVec)", "!isMissing(byObj)"})
        protected Object seqLengthMissing(VirtualFrame frame, RAbstractDoubleVector fromVec, RAbstractDoubleVector toVec, Object byObj, RMissing lengthOut, RMissing alongWith,
                        @Cached("create()") AsRealNode asRealby) {
            validateLength(frame, byObj, "by");
            double by = asRealby.execute(byObj);
            return doSeqLengthMissing(fromVec.getDataAt(0), toVec.getDataAt(0), by, false);
        }

        @Specialization(guards = {"validIntParams(fromVec, toVec)", "validIntParam(byVec)", "byVec.getDataAt(0) != 0"})
        protected RAbstractVector seqLengthMissing(VirtualFrame frame, RAbstractIntVector fromVec, RAbstractIntVector toVec, RAbstractIntVector byVec, RMissing lengthOut, RMissing alongWith,
                        @Cached("createBinaryProfile()") ConditionProfile directionProfile) {
            int by = byVec.getDataAt(0);
            int from = fromVec.getDataAt(0);
            int to = toVec.getDataAt(0);
            RIntSequence result;
            if (directionProfile.profile(from < to)) {
                if (by < 0) {
                    error.enter();
                    throw RError.error(this, RError.Message.WRONG_SIGN_IN_BY);
                }
                result = RDataFactory.createIntSequence(from, by, (to - from) / by + 1);
            } else {
                if (from == to) {
                    return RDataFactory.createIntVectorFromScalar(from);
                }
                if (by > 0) {
                    error.enter();
                    throw RError.error(this, RError.Message.WRONG_SIGN_IN_BY);
                }
                result = RDataFactory.createIntSequence(from, by, (from - to) / (-by) + 1);
            }
            return result;
        }

        /**
         * See comment in {@link #seqLengthByMissing}.
         */
        @Specialization(guards = {"!isMissing(byObj)"})
        protected Object seqLengthMissing(VirtualFrame frame, Object fromObj, Object toObj, Object byObj, RMissing lengthOut, RMissing alongWith,
                        @Cached("create()") AsRealNode asRealFrom, @Cached("create()") AsRealNode asRealTo, @Cached("create()") AsRealNode asRealby) {
            double from;
            boolean allInt = true;
            if (isMissing(fromObj)) {
                from = 1.0;
                allInt = false;
            } else {
                validateLength(frame, fromObj, "from");
                from = asRealFrom.execute(fromObj);
                validateDoubleParam(from, fromObj, "from");
                allInt &= isInt(fromObj);
            }
            double to;
            if (isMissing(toObj)) {
                to = 1.0;
                allInt = false;
            } else {
                validateLength(frame, toObj, "to");
                to = asRealFrom.execute(toObj);
                validateDoubleParam(to, toObj, "to");
                allInt &= isInt(toObj);
            }
            validateLength(frame, byObj, "by");
            allInt &= isInt(byObj);
            double by = asRealby.execute(byObj);
            return doSeqLengthMissing(from, to, by, allInt);
        }

        private static final double FEPS = 1E-10;

        private RAbstractVector doSeqLengthMissing(double from, double to, double by, boolean allInt) {
            double del = to - from;
            if (del == 0.0 && to == 0.0) {
                return RDataFactory.createDoubleVectorFromScalar(to);
            }
            double n = del / by;
            if (!isFinite(n)) {
                if (del == 0.0 && by == 0.0) {
                    // N.B. GNU R returns the original "from" argument (which might be missing)
                    return RDataFactory.createDoubleVectorFromScalar(from);
                } else {
                    error.enter();
                    // This should go away in an upcoming GNU R release
                    throw RError.error(this, seqFastPath ? RError.Message.INVALID_TFB_SD : RError.Message.INVALID_TFB);
                }
            }
            double dd = Math.abs(del) / Math.max(Math.abs(to), Math.abs(from));
            if (dd < 100 * RRuntime.EPSILON) {
                // N.B. GNU R returns the original "from" argument (which might be missing)
                return RDataFactory.createDoubleVectorFromScalar(from);
            }
            if (n > Integer.MAX_VALUE) {
                error.enter();
                throw RError.error(this, RError.Message.BY_TOO_SMALL);
            }
            if (n < -FEPS) {
                error.enter();
                throw RError.error(this, RError.Message.WRONG_SIGN_IN_BY);
            }
            RAbstractVector result;
            if (allInt) {
                result = RDataFactory.createIntSequence((int) from, (int) by, (int) (n + 1));
            } else {
                int nn = (int) (n + FEPS);
                if (nn == 0) {
                    return RDataFactory.createDoubleVectorFromScalar(from);
                }
                double datann = from + nn * by;
                // Added in 2.9.0
                boolean datannAdjust = (by > 0 && datann > to) || (by < 0 && datann < to);
                if (!datannAdjust) {
                    result = RDataFactory.createDoubleSequence(from, by, nn + 1);
                } else {
                    // GNU R creates actual vectors and adjusts the last element to "to"
                    // We can't do that with RDoubleSequence without breaking the intermediate
                    // values
                    double[] data = new double[nn + 1];
                    for (int i = 0; i < nn; i++) {
                        data[i] = from + i * by;
                    }
                    data[nn] = to;
                    result = RDataFactory.createDoubleVector(data, RDataFactory.COMPLETE_VECTOR);
                }
            }
            return result;

        }

        /*
         * seq(length.out=)
         */

        @Specialization(guards = "!isMissing(lengthOut)")
        protected RAbstractVector seqJustLength(VirtualFrame frame, RMissing from, RMissing to, RMissing by, Object lengthOut, RMissing alongWith,
                        @Cached("create()") AsRealNode asRealLen) {
            int n = checkLength(frame, lengthOut, asRealLen);
            return n == 0 ? RDataFactory.createEmptyIntVector() : RDataFactory.createIntSequence(1, 1, n);
        }

        // seq(along,with=)

        @Specialization(guards = "!isMissing(alongWith)")
        protected RAbstractVector seqFromJustAlong(VirtualFrame frame, RMissing from, RMissing to, RMissing by, RMissing lengthOut, Object alongWith) {
            int len = getLength(frame, alongWith);
            return len == 0 ? RDataFactory.createEmptyIntVector() : RDataFactory.createIntSequence(1, 1, len);
        }

        /*
         * The remaining non-error cases are when either length.out or along.with are provided in
         * addition to one or more of from/to/by. Unfortunately this is still a combinatorial
         * explosion of possibilities. We break this into three and the logic follows that in seq.c.
         *
         * The "oneNotMissing(alongWith, lengthOut)" ensure no overlap with the preceding
         * specializations where these were missing.
         *
         * N.B. Counter-intuitive; in the cases where "from" or "to" is missing, but "by" is
         * integral, GNU R returns an int sequence truncating "from" or "to". So seq.int(2.7, by=2,
         * length.out=4) produces [2,4,6,8], rather than [2.7,4.7,6.7,8.7]. But, seq.int(2.7,
         * by=2.1, length.out=4) produces [2.7,4.8,6.9,9.0]
         *
         * N.B. Also, there is no length check in these forms, so "seq.int(from=c(1,2), by=2,
         * length.out=10)" is legal.
         *
         * The only special case we define is "seq.int(from=k, length.lout=lout)" where "k" and
         * "lout" are integral (not just integer as programmers are casual about numeric literals
         * and often use "1" where "1L" is more appropriate).
         */

        @TypeSystemReference(RTypesFlatLayout.class)
        public abstract static class IsIntegralNumericNode extends Node {
            private final boolean checkLength;

            public abstract boolean execute(Object obj);

            public IsIntegralNumericNode(boolean checkLength) {
                this.checkLength = checkLength;
            }

            @Specialization
            protected boolean isIntegralNumericNode(Integer obj) {
                if (checkLength) {
                    return obj >= 0;
                } else {
                    return true;
                }
            }

            @Specialization
            protected boolean isIntegralNumericNode(RAbstractIntVector intVec) {
                return intVec.getLength() == 1 && (checkLength ? intVec.getDataAt(0) >= 0 : true);
            }

            @Specialization
            protected boolean isIntegralNumericNode(Double obj) {
                double d = obj;
                return d == (int) d && (checkLength ? d >= 0 : true);
            }

            @Specialization
            protected boolean isIntegralNumericNode(RAbstractDoubleVector doubleVec) {
                if (doubleVec.getLength() == 1) {
                    double d = doubleVec.getDataAt(0);
                    return d == (int) d && (checkLength ? d >= 0 : true);
                } else {
                    return false;
                }
            }

            @Fallback
            protected boolean isIntegralNumericNode(Object obj) {
                return false;
            }
        }

        @TypeSystemReference(RTypesFlatLayout.class)
        public abstract static class GetIntegralNumericNode extends Node {

            public abstract int execute(Object obj);

            @Specialization
            protected int getIntegralNumeric(Integer integer) {
                return integer;
            }

            @Specialization
            protected int getIntegralNumeric(RAbstractIntVector intVec) {
                return intVec.getDataAt(0);
            }

            @Specialization
            protected int getIntegralNumeric(Double d) {
                return (int) (double) d;
            }

            @Specialization
            protected int getIntegralNumeric(RAbstractDoubleVector doubleVec) {
                return (int) doubleVec.getDataAt(0);
            }

            @Fallback
            protected int getIntegralNumeric(Object obj) {
                throw RInternalError.shouldNotReachHere();
            }
        }

        public static GetIntegralNumericNode createGetIntegralNumericNode() {
            return GetIntegralNumericNodeGen.create();
        }

        public static IsIntegralNumericNode createIsIntegralNumericNodeNoLengthCheck() {
            return IsIntegralNumericNodeGen.create(false);
        }

        public static IsIntegralNumericNode createIsIntegralNumericNodeLengthCheck() {
            return IsIntegralNumericNodeGen.create(true);
        }

        // common idiom
        @Specialization(guards = {"fromCheck.execute(fromObj)", "lengthCheck.execute(lengthOut)"})
        protected RAbstractVector seqWithFromLengthIntegralNumeric(VirtualFrame frame, Object fromObj, RMissing toObj, RMissing byObj, Object lengthOut, RMissing alongWith,
                        @Cached("createGetIntegralNumericNode()") GetIntegralNumericNode getIntegralNumericNode,
                        @Cached("createIsIntegralNumericNodeNoLengthCheck()") IsIntegralNumericNode fromCheck,
                        @Cached("createIsIntegralNumericNodeLengthCheck()") IsIntegralNumericNode lengthCheck) {
            int from = getIntegralNumericNode.execute(fromObj);
            int lout = getIntegralNumericNode.execute(lengthOut);
            if (lout == 0) {
                return RDataFactory.createEmptyIntVector();
            }
            return RDataFactory.createDoubleSequence(from, 1, lout);

        }

        // "by" missing
        @Specialization(guards = {"oneNotMissing(alongWith, lengthOut)", "oneNotMissing(fromObj, toObj)"})
        protected RAbstractVector seqWithLength(VirtualFrame frame, Object fromObj, Object toObj, RMissing byObj, Object lengthOut, Object alongWith,
                        @Cached("create()") AsRealNode asRealFrom, @Cached("create()") AsRealNode asRealTo, @Cached("create()") AsRealNode asRealLen) {
            int lout = checkLengthAlongWith(frame, lengthOut, alongWith, asRealLen);
            if (lout == 0) {
                return RDataFactory.createEmptyIntVector();
            }
            boolean fromMissing = isMissing(fromObj);
            boolean toMissing = isMissing(toObj);
            double from = asRealFrom.execute(fromObj);
            double to = asRealFrom.execute(toObj);
            if (toMissing) {
                to = from + lout - 1;
            }
            if (fromMissing) {
                from = to - lout + 1;
            }
            validateDoubleParam(from, fromObj, "from");
            validateDoubleParam(to, toObj, "to");
            RAbstractVector result;
            if (lout > 2) {
                double by = (to - from) / (lout - 1);
                // double computedTo = from + (lout - 1) * by;
                /*
                 * GNU R sets data[lout-1] to "to". Experimentally using an RDoubleSequence
                 * sometimes produces a value that differs by a very small amount instead, so we use
                 * a vector.
                 */
                double[] data = new double[lout];
                data[0] = from;
                data[lout - 1] = to;
                for (int i = 1; i < lout - 1; i++) {
                    data[i] = from + i * by;
                }
                result = RDataFactory.createDoubleVector(data, RDataFactory.COMPLETE_VECTOR);
            } else {
                if (lout == 1) {
                    result = RDataFactory.createDoubleVectorFromScalar(from);
                } else {
                    if (seqFastPath && !fromMissing && isInt(fromObj) && (int) to == to) {
                        // differing behavior between seq.default and seq.int; may be fixed in
                        // upcoming GNU R release
                        result = RDataFactory.createIntVector(new int[]{(int) from, (int) to}, RDataFactory.COMPLETE_VECTOR);
                    } else {
                        result = RDataFactory.createDoubleVector(new double[]{from, to}, RDataFactory.COMPLETE_VECTOR);
                    }
                }
            }
            return result;
        }

        // "to" missing
        @Specialization(guards = {"oneNotMissing(alongWith, lengthOut)", "oneNotMissing(fromObj, byObj)"})
        protected RAbstractVector seqWithLength(VirtualFrame frame, Object fromObj, RMissing toObj, Object byObj, Object lengthOut, Object alongWith,
                        @Cached("create()") AsRealNode asRealFrom, @Cached("create()") AsRealNode asRealby, @Cached("create()") AsRealNode asRealLen) {
            int lout = checkLengthAlongWith(frame, lengthOut, alongWith, asRealLen);
            if (lout == 0) {
                return RDataFactory.createEmptyIntVector();
            }
            double from;
            if (isMissing(fromObj)) {
                from = 1.0;
            } else {
                from = asRealFrom.execute(fromObj);
                validateDoubleParam(from, fromObj, "from");
            }
            double by = asRealby.execute(byObj);
            validateDoubleParam(by, byObj, "by");
            double to = from + (lout - 1) * by;
            if (useIntVector(from, to, by)) {
                return RDataFactory.createIntSequence((int) from, (int) by, lout);
            } else {
                return RDataFactory.createDoubleSequence(from, by, lout);
            }
        }

        // "from" missing
        @Specialization(guards = {"oneNotMissing(alongWith, lengthOut)", "oneNotMissing(toObj, byObj)"})
        protected RAbstractVector seqWithLength(VirtualFrame frame, RMissing fromObj, Object toObj, Object byObj, Object lengthOut, Object alongWith,
                        @Cached("create()") AsRealNode asRealTo, @Cached("create()") AsRealNode asRealby, @Cached("create()") AsRealNode asRealLen) {
            int lout = checkLengthAlongWith(frame, lengthOut, alongWith, asRealLen);
            if (lout == 0) {
                return RDataFactory.createEmptyIntVector();
            }
            double to = asRealTo.execute(toObj);
            double by = asRealby.execute(byObj);
            double from = to - (lout - 1) * by;
            validateDoubleParam(to, toObj, "to");
            validateDoubleParam(by, byObj, "by");
            if (useIntVector(from, to, by)) {
                return RDataFactory.createIntSequence((int) from, (int) by, lout);
            } else {
                return RDataFactory.createDoubleSequence(from, by, lout);
            }
        }

        @Fallback
        protected RAbstractVector seqFallback(VirtualFrame frame, Object fromObj, Object toObj, Object byObj, Object lengthOut, Object alongWith) {
            error.enter();
            throw RError.error(this, RError.Message.TOO_MANY_ARGS);
        }

        // Guard methods

        public static boolean validDoubleParams(RAbstractDoubleVector from, RAbstractDoubleVector to) {
            return from.getLength() == 1 && to.getLength() == 1 && isFinite(from.getDataAt(0)) && isFinite(to.getDataAt(0));
        }

        public static final boolean validIntParams(RAbstractIntVector from, RAbstractIntVector to) {
            return validIntParam(from) && validIntParam(to);
        }

        public static final boolean validIntParam(RAbstractIntVector vec) {
            return vec.getLength() == 1 && vec.getDataAt(0) != RRuntime.INT_NA;
        }

        public final int getLength(VirtualFrame frame, Object obj) {
            return lengthNode.executeInteger(frame, obj);
        }

        public static final boolean isNumeric(Object obj) {
            return obj instanceof Double || obj instanceof Integer || obj instanceof RAbstractDoubleVector || obj instanceof RAbstractIntVector;
        }

        public static final boolean isInt(Object obj) {
            return obj instanceof Integer || obj instanceof RAbstractIntVector;
        }

        public static final boolean isMissing(Object obj) {
            return obj == RMissing.instance || obj == REmpty.instance;
        }

        public static final boolean oneNotMissing(Object obj1, Object obj2) {
            return !isMissing(obj1) || !isMissing(obj2);
        }

        // Utility methods

        private static boolean isFinite(double v) {
            return !(RRuntime.isNAorNaN(v) || Double.isInfinite(v));
        }

        private int validateIntParam(int v, String vName) {
            if (RRuntime.isNA(v)) {
                error.enter();
                throw RError.error(this, RError.Message.CANNOT_BE_INVALID, vName);
            }
            return v;
        }

        /**
         * Unless {@code vObj} is missing, check whether {@code isFinite}. Return {@code v}
         * unmodified.
         */
        private double validateDoubleParam(double v, Object vObj, String vName) {
            if (vObj != RMissing.instance) {
                if (!isFinite(v)) {
                    error.enter();
                    throw RError.error(this, RError.Message.CANNOT_BE_INVALID, vName);
                }
            }
            return v;
        }

        /**
         * Unless {@code obj} is missing, check whether length is 1.
         */
        private void validateLength(VirtualFrame frame, Object obj, String vName) {
            if (obj != RMissing.instance) {
                if (getLength(frame, obj) != 1) {
                    error.enter();
                    throw RError.error(this, RError.Message.MUST_BE_SCALAR, vName);
                }
            }
        }

        private int checkLength(VirtualFrame frame, Object lengthOut, AsRealNode asRealLen) {
            double len = asRealLen.execute(lengthOut);
            if (RRuntime.isNAorNaN(len) || len <= -0.5) {
                error.enter();
                throw RError.error(this, seqFastPath ? RError.Message.MUST_BE_POSITIVE_SD : RError.Message.MUST_BE_POSITIVE, seqFastPath ? "length" : "length.out");
            }
            if (getLength(frame, lengthOut) != 1) {
                RError.warning(this, RError.Message.FIRST_ELEMENT_USED, "length.out");
            }
            return (int) Math.ceil(len);
        }

        private static boolean isInIntRange(double d) {
            return d <= Integer.MAX_VALUE && d >= Integer.MIN_VALUE;
        }

        private static boolean useIntVector(double from, double to, double by) {
            return (int) by == by && isInIntRange(from) && isInIntRange(to);
        }

        private int checkLengthAlongWith(VirtualFrame frame, Object lengthOut, Object alongWith, AsRealNode asRealLen) {
            if (alongWith != RMissing.instance) {
                return getLength(frame, alongWith);
            } else if (lengthOut != RMissing.instance) {
                return checkLength(frame, lengthOut, asRealLen);
            } else {
                throw RInternalError.shouldNotReachHere();
            }
        }

        private static int effectiveLength(double n1, double n2) {
            double r = Math.abs(n2 - n1);
            return (int) (r + 1 + FLT_EPSILON);
        }

        private int checkVecLength(double from, double to) {
            double r = Math.abs(to - from);
            if (r > Integer.MAX_VALUE) {
                error.enter();
                throw RError.error(this, RError.Message.TOO_LONG_VECTOR);
            }
            int length = (int) (r + 1 + FLT_EPSILON);
            return length;
        }

        /**
         * Maps from {@code from} and {@code to} to the {@link RSequence} interface.
         */
        private static RIntSequence createRIntSequence(int from, int to, ConditionProfile directionProfile) {
            if (directionProfile.profile(from <= to)) {
                int length = to - from + 1;
                return RDataFactory.createIntSequence(from, 1, length);
            } else {
                int length = from - to + 1;
                return RDataFactory.createIntSequence(from, -1, length);
            }
        }

        /**
         * Similar to {@link #createRIntSequence} but chooses the type of sequence based on the
         * argument values.
         */
        private RAbstractVector createRSequence(double from, double to, ConditionProfile directionProfile) {
            boolean useInt = from <= Integer.MAX_VALUE && (from == (int) from);
            int length = 0;
            if (useInt) {
                if (from <= Integer.MIN_VALUE || from > Integer.MAX_VALUE) {
                    useInt = false;
                } else {
                    /* r := " the effective 'to' " of from:to */
                    double dn = Math.abs(to - from) + 1 + FLT_EPSILON;
                    length = (int) dn;
                    double r = from + ((from <= to) ? dn - 1 : -(dn - 1));
                    if (r <= Integer.MIN_VALUE || r > Integer.MAX_VALUE) {
                        useInt = false;
                    }
                }
            }
            if (useInt) {
                RIntSequence result = RDataFactory.createIntSequence((int) from, directionProfile.profile(from <= to) ? 1 : -1, length);
                return result;
            } else {
                length = checkVecLength(from, to);
                RDoubleSequence result = RDataFactory.createDoubleSequence(from, directionProfile.profile(from <= to) ? 1 : -1, length);
                return result;
            }
        }
    }
}
