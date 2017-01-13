/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include <R.h>
#include <Rinternals.h>
#include <R_ext/Rdynload.h>
#include "testrffi.h"

static const R_CMethodDef CEntries[]  = {
    {"dotCModifiedArguments", (DL_FUNC) &dotCModifiedArguments, 4},
    {NULL, NULL, 0}
};

#define CALLDEF(name, n)  {#name, (DL_FUNC) &name, n}

static const R_CallMethodDef CallEntries[] = {
	    CALLDEF(addInt, 2),
	    CALLDEF(addDouble, 2),
	    CALLDEF(populateIntVector, 1),
	    CALLDEF(populateLogicalVector, 1),
	    CALLDEF(createExternalPtr, 3),
	    CALLDEF(getExternalPtrAddr, 1),
	    CALLDEF(invoke_TYPEOF, 1),
	    CALLDEF(invoke_error, 1),
	    CALLDEF(dot_external_access_args, 1),
	    CALLDEF(invoke_isString, 1),
	    CALLDEF(invoke12, 12),
	    CALLDEF(interactive, 0),
	    CALLDEF(tryEval, 2),
	    CALLDEF(rHomeDir, 0),
	    CALLDEF(nestedCall1, 2),
	    CALLDEF(nestedCall2, 1),
	    CALLDEF(r_home, 0),
	    CALLDEF(mkStringFromChar, 0),
	    CALLDEF(mkStringFromBytes, 0),
	    CALLDEF(null, 0),
	    CALLDEF(iterate_iarray, 1),
	    CALLDEF(iterate_iptr, 1),
	    CALLDEF(preserve_object, 0),
	    CALLDEF(release_object, 1),
	    CALLDEF(findvar, 2),
	    {NULL, NULL, 0}
};

void
R_init_testrffi(DllInfo *dll)
{
    R_registerRoutines(dll, CEntries, CallEntries, NULL, NULL);
}
