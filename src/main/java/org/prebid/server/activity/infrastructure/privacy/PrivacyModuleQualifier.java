package org.prebid.server.activity.infrastructure.privacy;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PrivacyModuleQualifier {

    @JsonProperty(Names.US_NAT)
    US_NAT;

    public static class Names {

        public static final String US_NAT = "iab.usgeneral";
    }
}
