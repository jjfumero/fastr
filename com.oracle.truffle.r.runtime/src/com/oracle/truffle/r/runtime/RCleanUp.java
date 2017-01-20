/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2014,  The R Core Team
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime;

import java.util.ArrayList;

import com.oracle.truffle.r.runtime.RStartParams.SA_TYPE;
import com.oracle.truffle.r.runtime.context.ConsoleHandler;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.ffi.RFFIFactory;
import com.oracle.truffle.r.runtime.instrument.InstrumentationState;

public class RCleanUp {

    private static ArrayList<InstrumentationState.CleanupHandler> cleanupHandlers = new ArrayList<>();

    public static void registerCleanupHandler(InstrumentationState.CleanupHandler cleanupHandler) {
        cleanupHandlers.add(cleanupHandler);
    }

    public static void cleanUp(SA_TYPE saveType, int status, boolean runLast) {
        if (RInterfaceCallbacks.R_CleanUp.isOverridden()) {
            RFFIFactory.getRFFI().getREmbedRFFI().cleanUp(saveType.ordinal(), status, runLast ? 1 : 0);
        } else {
            stdCleanUp(saveType, status, runLast);
        }
    }

    public static void stdCleanUp(final SA_TYPE saveActionIn, int status, boolean runLast) {
        // Output is not diverted to sink
        ConsoleHandler consoleHandler = RContext.getInstance().getConsoleHandler();
        SA_TYPE saveAction = saveActionIn;
        if (saveAction == SA_TYPE.DEFAULT) {
            saveAction = RContext.getInstance().getStartParams().getSaveAction();
        }
        if (saveAction == SA_TYPE.SAVEASK) {
            if (consoleHandler.isInteractive()) {
                W: while (true) {
                    consoleHandler.setPrompt("");
                    consoleHandler.print("Save workspace image? [y/n/c]: ");
                    String response = consoleHandler.readLine();
                    if (response == null) {
                        saveAction = SA_TYPE.NOSAVE;
                        break;
                    }
                    if (response.length() == 0) {
                        continue;
                    }
                    switch (response.charAt(0)) {
                        case 'c':
                            consoleHandler.setPrompt("> ");
                            throw new JumpToTopLevelException();
                        case 'y':
                            saveAction = SA_TYPE.SAVE;
                            break W;
                        case 'n':
                            saveAction = SA_TYPE.NOSAVE;
                            break W;
                        default:
                            continue;
                    }
                }
            } else {
                saveAction = RContext.getInstance().getStartParams().getSaveAction();
            }
        }

        switch (saveAction) {
            case SAVE:
                if (runLast) {
                    runDotLast();
                }
                /*
                 * we do not have an efficient way to tell if the global environment is "dirty", so
                 * we save always
                 */
                RContext.getEngine().checkAndRunStartupShutdownFunction("sys.save.image", new String[]{"\".RData\""});
                RContext.getInstance().getConsoleHandler().flushHistory();
                break;

            case NOSAVE:
                if (runLast) {
                    runDotLast();
                }
                break;

            case SUICIDE:
            default:

        }
        for (InstrumentationState.CleanupHandler cleanupHandler : cleanupHandlers) {
            try {
                cleanupHandler.cleanup(status);
            } catch (Throwable t) {
                RInternalError.reportError(t);
            }
        }
        // TODO run exit finalizers (FFI)
        // TODO clean tmpdir
        throw new ExitException(status);

    }

    private static void runDotLast() {
        RContext.getEngine().checkAndRunStartupShutdownFunction(".Last");
        // TODO errors should return to toplevel if interactive
        RContext.getEngine().checkAndRunStartupShutdownFunction(".Last.sys");
    }
}
