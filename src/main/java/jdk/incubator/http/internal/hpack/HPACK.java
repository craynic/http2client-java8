/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.incubator.http.internal.hpack;

import jdk.incubator.http.internal.common.SysLogger;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.hpack.HPACK.Logger.Level;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import java9.util.Maps;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.EXTRA;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.NONE;
import static jdk.incubator.http.internal.hpack.HPACK.Logger.Level.NORMAL;

/**
 * Internal utilities and stuff.
 */
public final class HPACK {

    private static final RootLogger LOGGER;
    private static final Map<String, Level> logLevels =
            Maps.of("NORMAL", NORMAL, "EXTRA", EXTRA);

    static {
        String PROPERTY = "jdk.internal.httpclient.hpack.log.level";

        String value = AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty(PROPERTY));

        if (value == null) {
            LOGGER = new RootLogger(NONE);
        } else {
            String upperCasedValue = value.toUpperCase();
            Level l = logLevels.get(upperCasedValue);
            if (l == null) {
                LOGGER = new RootLogger(NONE);
                LOGGER.log(SysLogger.Level.INFO,
                        () -> format("%s value '%s' not recognized (use %s); logging disabled",
                                     PROPERTY, value, logLevels.keySet().stream().collect(joining(", "))));
            } else {
                LOGGER = new RootLogger(l);
                LOGGER.log(SysLogger.Level.DEBUG,
                        () -> format("logging level %s", l));
            }
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    private HPACK() { }

    /**
     * The purpose of this logger is to provide means of diagnosing issues _in
     * the HPACK implementation_. It's not a general purpose logger.
     */
    // implements System.Logger to make it possible to skip this class
    // when looking for the Caller.
    public static class Logger implements jdk.incubator.http.internal.common.SysLogger {
        /**
         * Log detail level.
         */
        public enum Level {

            NONE(0, SysLogger.Level.OFF),
            NORMAL(1, SysLogger.Level.DEBUG),
            EXTRA(2, SysLogger.Level.TRACE);

            private final int level;
            final SysLogger.Level systemLevel;

            Level(int i, SysLogger.Level system) {
                level = i;
                systemLevel = system;
            }

            public final boolean implies(Level other) {
                return this.level >= other.level;
            }
        }

        private final String name;
        private final Level level;
        private final String path;
        private final SysLogger logger;

        private Logger(String path, String name, Level level) {
            this(path, name, level, null);
        }

        private Logger(String p, String name, Level level, SysLogger logger) {
            this.path = p;
            this.name = name;
            this.level = level;
            this.logger = Utils.getHpackLogger(path::toString, level.systemLevel);
        }

        public final String getName() {
            return name;
        }

        @Override
        public boolean isLoggable(SysLogger.Level level) {
            return logger.isLoggable(level);
        }

        @Override
        public void log(SysLogger.Level level, ResourceBundle bundle, String msg, Throwable thrown) {
            logger.log(level, bundle, msg,thrown);
        }

        @Override
        public void log(SysLogger.Level level, ResourceBundle bundle, String format, Object... params) {
            logger.log(level, bundle, format, params);
        }

        /*
         * Usual performance trick for logging, reducing performance overhead in
         * the case where logging with the specified level is a NOP.
         */

        public boolean isLoggable(Level level) {
            return this.level.implies(level);
        }

        public void log(Level level, Supplier<String> s) {
            if (this.level.implies(level)) {
                logger.log(level.systemLevel, s);
            }
        }

        public Logger subLogger(String name) {
            return new Logger(path + "/" + name, name, level);
        }
    }

    private static final class RootLogger extends Logger {

        protected RootLogger(Level level) {
            super("hpack", "hpack", level);
        }

    }
}
