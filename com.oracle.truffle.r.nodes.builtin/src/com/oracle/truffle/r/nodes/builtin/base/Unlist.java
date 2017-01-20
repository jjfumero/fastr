/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2012, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.toBoolean;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.nodes.attributes.SpecialAttributesFunctions.GetNamesAttributeNode;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.UnlistNodeGen.RecursiveLengthNodeGen;
import com.oracle.truffle.r.nodes.unary.PrecedenceNode;
import com.oracle.truffle.r.nodes.unary.PrecedenceNodeGen;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RFunction;
import com.oracle.truffle.r.runtime.data.RLanguage;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RRaw;
import com.oracle.truffle.r.runtime.data.RStringVector;
import com.oracle.truffle.r.runtime.data.RTypes;
import com.oracle.truffle.r.runtime.data.model.RAbstractContainer;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

@RBuiltin(name = "unlist", kind = INTERNAL, parameterNames = {"x", "recursive", "use.names"}, behavior = PURE)
public abstract class Unlist extends RBuiltinNode {

    // portions of the algorithm were transcribed from GNU R

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.arg("recursive").asLogicalVector().findFirst(RRuntime.LOGICAL_TRUE).map(toBoolean());
        casts.arg("use.names").asLogicalVector().findFirst(RRuntime.LOGICAL_TRUE).map(toBoolean());
    }

    @Child private PrecedenceNode precedenceNode = PrecedenceNodeGen.create();
    @Child private Length lengthNode;
    @Child private RecursiveLength recursiveLengthNode;
    @Child private GetNamesAttributeNode getNames = GetNamesAttributeNode.create();

    @TypeSystemReference(RTypes.class)
    protected abstract static class RecursiveLength extends Node {

        public abstract int executeInt(VirtualFrame frame, Object vector);

        @Child private RecursiveLength recursiveLengthNode;

        private int getRecursiveLength(VirtualFrame frame, Object operand) {
            initRecursiveLengthNode();
            return recursiveLengthNode.executeInt(frame, operand);
        }

        private void initRecursiveLengthNode() {
            if (recursiveLengthNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveLengthNode = insert(RecursiveLengthNodeGen.create());
            }
        }

        @Specialization
        protected int getLength(@SuppressWarnings("unused") RNull vector) {
            return 0;
        }

        @Specialization
        protected int getLength(@SuppressWarnings("unused") RLanguage l) {
            // language object do not get expanded - as such their length for the purpose of unlist
            // is 1
            return 1;
        }

        @Specialization
        protected int getLength(@SuppressWarnings("unused") RFunction l) {
            return 1;
        }

        @Specialization(guards = "!isVectorList(vector)")
        protected int getLength(RAbstractVector vector) {
            return vector.getLength();
        }

        @Specialization(guards = "isVectorList(vector)")
        protected int getLengthList(VirtualFrame frame, RAbstractVector vector) {
            int totalSize = 0;
            for (int i = 0; i < vector.getLength(); i++) {
                Object data = vector.getDataAtAsObject(i);
                totalSize += getRecursiveLength(frame, data);
            }
            return totalSize;
        }

        @Specialization
        protected int getLengthPairList(VirtualFrame frame, RPairList list) {
            int totalSize = 0;
            for (RPairList item : list) {
                totalSize += getRecursiveLength(frame, item.car());
            }
            return totalSize;
        }

        protected boolean isVectorList(RAbstractVector vector) {
            return vector instanceof RList;
        }
    }

    private int getLength(VirtualFrame frame, Object operand) {
        initLengthNode();
        return lengthNode.executeInt(frame, operand);
    }

    private void initLengthNode() {
        if (lengthNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lengthNode = insert(LengthNodeGen.create());
        }
    }

    private int getRecursiveLength(VirtualFrame frame, Object operand) {
        initRecursiveLengthNode();
        return recursiveLengthNode.executeInt(frame, operand);
    }

    private void initRecursiveLengthNode() {
        if (recursiveLengthNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            recursiveLengthNode = insert(RecursiveLengthNodeGen.create());
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected RNull unlist(RNull vector, boolean recursive, boolean useNames) {
        return RNull.instance;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isEmpty(list)")
    protected RNull unlistEmptyList(RList list, boolean recursive, boolean useNames) {
        return RNull.instance;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isEmpty(list)")
    protected RNull unlistEmptyList(RPairList list, boolean recursive, boolean useNames) {
        return RNull.instance;
    }

    // TODO: initially unlist was on the slow path - hence initial recursive implementation is on
    // the slow path as well; ultimately we may consider (non-recursive) optimization
    @Specialization(guards = "!isEmpty(list)")
    protected Object unlistList(VirtualFrame frame, RList list, boolean recursive, boolean useNames) {
        int precedence = PrecedenceNode.NO_PRECEDENCE;
        int totalSize = 0;
        for (int i = 0; i < list.getLength(); i++) {
            Object data = list.getDataAt(i);
            precedence = Math.max(precedence, precedenceNode.executeInteger(data, recursive));
            if (recursive) {
                totalSize += getRecursiveLength(frame, data);
            } else {
                totalSize += getLength(frame, data);
            }
        }
        // If the precedence is still NO_PRECEDENCE the result is RNull.instance
        if (precedence == PrecedenceNode.NO_PRECEDENCE) {
            return RNull.instance;
        } else {
            return unlistHelper(list, recursive, useNames, precedence, totalSize);
        }
    }

    @Specialization(guards = "!isEmpty(list)")
    protected Object unlistPairList(VirtualFrame frame, RPairList list, boolean recursive, boolean useNames) {
        // TODO: unlist((pairlist(pairlist(1)), recursive=FALSE), see unit tests
        // Note: currently, we convert to list any pair-list that we encounter along the way, this
        // is sub-optimal, but the assumption is that pair-lists do not show up a lot
        return unlistList(frame, list.toRList(), recursive, useNames);
    }

    @SuppressWarnings("unused")
    @Fallback
    protected Object unlist(Object o, Object recursive, Object useNames) {
        return o;
    }

    @TruffleBoundary
    private RAbstractVector unlistHelper(RList list, boolean recursive, boolean useNames, int precedence, int totalSize) {
        String[] namesData = useNames ? new String[totalSize] : null;
        NamesInfo namesInfo = useNames ? new NamesInfo() : null;
        switch (precedence) {
            case PrecedenceNode.RAW_PRECEDENCE: {
                byte[] result = new byte[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperRaw(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperRaw(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createRawVector(result, namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.LOGICAL_PRECEDENCE: {
                byte[] result = new byte[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperLogical(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperLogical(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createLogicalVector(result, RDataFactory.INCOMPLETE_VECTOR,
                                namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.INT_PRECEDENCE: {
                int[] result = new int[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperInt(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperInt(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createIntVector(result, RDataFactory.INCOMPLETE_VECTOR,
                                namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.DOUBLE_PRECEDENCE: {
                double[] result = new double[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperDouble(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperDouble(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createDoubleVector(result, RDataFactory.INCOMPLETE_VECTOR,
                                namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.COMPLEX_PRECEDENCE: {
                double[] result = new double[totalSize << 1];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperComplex(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperComplex(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createComplexVector(result, RDataFactory.INCOMPLETE_VECTOR,
                                namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.STRING_PRECEDENCE: {
                String[] result = new String[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperString(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperString(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createStringVector(result, RDataFactory.INCOMPLETE_VECTOR,
                                namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }
            case PrecedenceNode.LIST_PRECEDENCE:
            case PrecedenceNode.EXPRESSION_PRECEDENCE: {
                Object[] result = new Object[totalSize];
                if (!recursive) {
                    RStringVector ln = getNames.getNames(list);
                    RStringVector listNames = useNames && ln != null ? ln : null;
                    int position = 0;
                    for (int i = 0; i < list.getLength(); i++) {
                        if (list.getDataAt(i) != RNull.instance) {
                            position = unlistHelperList(result, namesData, position, namesInfo, list.getDataAt(i), null, itemName(listNames, i), recursive, useNames);
                        }
                    }
                } else {
                    unlistHelperList(result, namesData, 0, namesInfo, list, null, null, recursive, useNames);
                }
                return RDataFactory.createList(result, namesInfo != null && namesInfo.namesAssigned ? RDataFactory.createStringVector(namesData, RDataFactory.INCOMPLETE_VECTOR) : null);
            }

            default:
                throw RInternalError.unimplemented();
        }
    }

    protected boolean isVectorList(RAbstractVector vector) {
        return vector instanceof RList;
    }

    private static class NamesInfo {
        private int count = 0;
        private int seqNo = 0;
        private int firstPos = 0;
        private boolean namesAssigned = false;

        private void reset() {
            this.firstPos = -1;
            this.seqNo = 0;
            this.count = 0;
        }
    }

    @TruffleBoundary
    private int unlistHelperRaw(byte[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperRaw(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = unlistValueRaw(cur);
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = unlistValueRaw(o);
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperLogical(byte[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperLogical(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = unlistValueLogical(cur);
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = unlistValueLogical(o);
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperInt(int[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperInt(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = unlistValueInt(cur);
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = unlistValueInt(o);
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperDouble(double[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperDouble(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = unlistValueDouble(cur);
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = unlistValueDouble(o);
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperComplex(double[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperComplex(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position >> 1, namesData, namesInfo, useNames);
                    RComplex val = unlistValueComplex(cur);
                    result[position++] = val.getRealPart();
                    result[position++] = val.getImaginaryPart();
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position >> 1, namesData, namesInfo, useNames);
            RComplex val = unlistValueComplex(o);
            result[position++] = val.getRealPart();
            result[position++] = val.getImaginaryPart();
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperList(Object[] result, String[] namesData, int pos, NamesInfo namesInfo, Object obj, String outerBase, String tag, boolean recursive, boolean useNames) {
        Object o = handlePairList(obj);
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperList(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = cur;
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = o;
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    @TruffleBoundary
    private int unlistHelperString(String[] result, String[] namesData, int pos, NamesInfo namesInfo, Object oIn, String outerBase, String tag, boolean recursive, boolean useNames) {
        int position = pos;
        int saveFirstPos = 0;
        int saveSeqNo = 0;
        int saveCount = 0;
        String base = outerBase;
        if (tag != null) {
            base = newBase(outerBase, tag);
            saveFirstPos = namesInfo.firstPos;
            saveSeqNo = namesInfo.seqNo;
            saveCount = namesInfo.count;
            namesInfo.reset();
        }

        Object o = handlePairList(oIn);
        if (o instanceof RAbstractVector) {
            RAbstractVector v = (RAbstractVector) o;
            RStringVector ln = getNames.getNames(v);
            RStringVector listNames = useNames && ln != null ? ln : null;
            for (int i = 0; i < v.getLength(); i++) {
                String name = itemName(listNames, i);
                Object cur = v.getDataAtAsObject(i);
                if (v instanceof RList && recursive) {
                    position = unlistHelperString(result, namesData, position, namesInfo, cur, base, name, recursive, useNames);
                } else {
                    assignName(name, base, position, namesData, namesInfo, useNames);
                    result[position++] = unlistValueString(v.getDataAtAsObject(i));
                }
            }
        } else if (o != RNull.instance) {
            assignName(null, base, position, namesData, namesInfo, useNames);
            result[position++] = unlistValueString(o);
        }
        fixupName(tag, base, namesData, namesInfo, useNames, saveFirstPos, saveCount, saveSeqNo);
        return position;
    }

    private static void fixupName(String tag, String base, String[] namesData, NamesInfo namesInfo, boolean useNames, int saveFirstPos, int saveCount, int saveSeqNo) {
        if (useNames) {
            if (tag != null) {
                if (namesInfo.firstPos >= 0 && namesInfo.count == 1) {
                    namesData[namesInfo.firstPos] = base;
                }
                namesInfo.firstPos = saveFirstPos;
                namesInfo.count = saveCount;
            }
            namesInfo.seqNo = namesInfo.seqNo + saveSeqNo;
        }
    }

    private static void assignName(String name, String base, int position, String[] namesData, NamesInfo namesInfo, boolean useNames) {
        if (useNames) {
            if (name == null && namesInfo.count == 0) {
                namesInfo.firstPos = position;
            }
            namesInfo.count++;
            namesData[position] = newName(base, name, namesInfo);
        }
    }

    private static String itemName(RStringVector names, int i) {
        if (names == null || names.getDataAt(i).equals(RRuntime.NAMES_ATTR_EMPTY_VALUE)) {
            return null;
        } else {
            return names.getDataAt(i);
        }
    }

    private static String newBase(String base, String tag) {
        if (base != null && tag != null) {
            return createCompositeName(base, tag);
        } else if (base != null) {
            return base;
        } else if (tag != null) {
            return tag;
        } else {
            return RRuntime.NAMES_ATTR_EMPTY_VALUE;
        }
    }

    private static String newName(String base, String tag, NamesInfo namesInfo) {
        namesInfo.seqNo++;
        if (base != null && tag != null) {
            namesInfo.namesAssigned = true;
            return createCompositeName(base, tag);
        } else if (base != null) {
            namesInfo.namesAssigned = true;
            return createCompositeName(base, namesInfo.seqNo);
        } else if (tag != null) {
            namesInfo.namesAssigned = true;
            return tag;
        } else {
            return RRuntime.NAMES_ATTR_EMPTY_VALUE;
        }
    }

    @TruffleBoundary
    private static String createCompositeName(String s1, String s2) {
        return s1 + "." + s2;
    }

    @TruffleBoundary
    private static String createCompositeName(String s1, int i) {
        return s1 + i;
    }

    private static String unlistValueString(Object cur) {
        if (cur instanceof Double) {
            Double d = (Double) cur;
            return RRuntime.isNAorNaN(d) ? RRuntime.STRING_NA : RContext.getRRuntimeASTAccess().encodeDouble(d);
        } else if (cur instanceof RComplex) {
            RComplex c = (RComplex) cur;
            return c.isNA() ? RRuntime.STRING_NA : RContext.getRRuntimeASTAccess().encodeComplex(c);
        } else {
            return RRuntime.toString(cur);
        }
    }

    private static RComplex unlistValueComplex(Object dataAtAsObject) {
        if (dataAtAsObject instanceof RComplex) {
            return (RComplex) dataAtAsObject;
        } else if (dataAtAsObject instanceof Double) {
            double result = unlistValueDouble(dataAtAsObject);
            if (RRuntime.isNA(result)) {
                return RRuntime.createComplexNA();
            } else {
                return RDataFactory.createComplex(result, 0.0);
            }
        } else {
            int result = unlistValueInt(dataAtAsObject);
            if (RRuntime.isNA(result)) {
                return RRuntime.createComplexNA();
            } else {
                return RDataFactory.createComplex(result, 0.0);
            }
        }
    }

    private static double unlistValueDouble(Object dataAtAsObject) {
        if (dataAtAsObject instanceof Double) {
            return (double) dataAtAsObject;
        } else {
            int result = unlistValueInt(dataAtAsObject);
            if (RRuntime.isNA(result)) {
                return RRuntime.DOUBLE_NA;
            } else {
                return result;
            }
        }
    }

    private static int unlistValueInt(Object dataAtAsObject) {
        if (dataAtAsObject instanceof RRaw) {
            RRaw rRaw = (RRaw) dataAtAsObject;
            return RRuntime.raw2int(rRaw);
        } else if (dataAtAsObject instanceof Byte) {
            return RRuntime.logical2int((byte) dataAtAsObject);
        } else {
            return (int) dataAtAsObject;
        }
    }

    private static byte unlistValueLogical(Object dataAtAsObject) {
        if (dataAtAsObject instanceof RRaw) {
            RRaw rRaw = (RRaw) dataAtAsObject;
            return RRuntime.raw2logical(rRaw);
        } else {
            return (byte) dataAtAsObject;
        }
    }

    private static byte unlistValueRaw(Object dataAtAsObject) {
        return ((RRaw) dataAtAsObject).getValue();
    }

    protected static boolean isEmpty(RAbstractContainer vector) {
        return vector.getLength() == 0;
    }

    protected static boolean isOneNull(RList list) {
        return list.getLength() == 1 && list.getDataAt(0) == RNull.instance;
    }

    private static Object handlePairList(Object o) {
        return o instanceof RPairList ? ((RPairList) o).toRList() : o;
    }
}
