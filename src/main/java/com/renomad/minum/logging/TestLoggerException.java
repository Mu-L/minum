package com.renomad.minum.logging;

import java.io.Serial;

/**
 * This is an implementation of {@link RuntimeException}, scoped
 * for the TestLogger.
 */
public class TestLoggerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 7590640788970680799L;

    /**
     * See {@link RuntimeException#RuntimeException(String)}
     */
    public TestLoggerException(String message) {
        super(message);
    }
}