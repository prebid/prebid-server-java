package com.scientiamobile.wurfl.core.exc;

public class CapabilityNotDefinedException extends WURFLRuntimeException {

    public CapabilityNotDefinedException(String message) {
        super(message);
    }

    public CapabilityNotDefinedException(String message, Throwable cause) {
        super(message, cause);
    }
}
