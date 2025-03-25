package org.prebid.server.activity.infrastructure.privacy;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PrivacyModuleQualifier {

    @JsonProperty(Names.US_NAT)
    US_NAT(Names.US_NAT),

    @JsonProperty(Names.US_CUSTOM_LOGIC)
    US_CUSTOM_LOGIC(Names.US_CUSTOM_LOGIC);

    private final String moduleName;

    PrivacyModuleQualifier(String moduleName) {
        this.moduleName = moduleName;
    }

    public String moduleName() {
        return moduleName;
    }

    public static class Names {

        public static final String US_NAT = "iab.usgeneral";
        public static final String US_CUSTOM_LOGIC = "iab.uscustomlogic";
    }
}
