package com.oracle.truffle.r.nodes.builtin.fastr;

import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDouble;

@RBuiltin(name = "nanotime", kind = PRIMITIVE, parameterNames = {""}, behavior = COMPLEX)
public abstract class FastRNanotime extends RBuiltinNode {
    @Specialization
    protected RDouble nanotime() {
        return (RDouble.valueOf(System.nanoTime()));
    }
}
