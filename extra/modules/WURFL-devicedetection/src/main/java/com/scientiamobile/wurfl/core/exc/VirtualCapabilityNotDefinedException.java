package com.scientiamobile.wurfl.core.exc;

public class VirtualCapabilityNotDefinedException extends WURFLRuntimeException {

    public VirtualCapabilityNotDefinedException(String message) {
        super(message);
    }

    public VirtualCapabilityNotDefinedException(String message, Throwable cause) {
        super(message, cause);
    }
}
