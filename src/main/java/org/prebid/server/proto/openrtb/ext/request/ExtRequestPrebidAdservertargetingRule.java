package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.adservertargeting
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidAdservertargetingRule {

    String key;

    Source source;

    String value;

    public enum Source {

        BIDREQUEST,
        X_STATIC,
        BIDRESPONSE;

        @JsonValue
        @Override
        public String toString() {
            return this == X_STATIC ? "static"
                    : name().toLowerCase();
        }
    }
}
