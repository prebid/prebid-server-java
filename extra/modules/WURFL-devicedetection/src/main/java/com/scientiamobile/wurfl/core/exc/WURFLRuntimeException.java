package com.scientiamobile.wurfl.core.exc;

public class WURFLRuntimeException extends RuntimeException {

    public WURFLRuntimeException(String message) {
        super(message);
    }

    public WURFLRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
