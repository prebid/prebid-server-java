package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PurposeOneTreatmentInterpretation {

    IGNORE,
    @JsonProperty("no-access-allowed")
    NO_ACCESS_ALLOWED,
    @JsonProperty("access-allowed")
    ACCESS_ALLOWED
}
