package org.rtb.vexing.adapter;

public class PreBidRequestException extends RuntimeException {

    public PreBidRequestException(String message) {
        super(message);
    }

    public PreBidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
