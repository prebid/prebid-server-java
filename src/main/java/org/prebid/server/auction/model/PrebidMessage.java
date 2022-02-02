package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class PrebidMessage {

    Type type;

    String message;

    public enum Type {

        generic(999),
        account_level_debug_disabled(10002),
        bidder_level_debug_disabled(10003);

        private final Integer code;

        Type(final Integer errorCode) {
            this.code = errorCode;
        }

        public Integer getCode() {
            return code;
        }
    }
}
