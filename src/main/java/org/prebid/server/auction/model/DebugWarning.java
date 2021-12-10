package org.prebid.server.auction.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class DebugWarning {

    int code;

    String message;

    public enum Code {
        invalid_privacy_consent(10001),
        account_level_debug_disabled(10002),
        bidder_level_debug_disabled(10003),
        multibid(10005),
        invalid_tracking_url_for_vastxml(10006),
        bidrequest_contains_both_app_and_site(10007),
        invalid_price_in_bid(10008),
        unknown(10999);

        private int code;

        Code(int warningCode) {
            this.code = warningCode;
        }

        public int getCode() {
            return this.code;
        }
    }
}
