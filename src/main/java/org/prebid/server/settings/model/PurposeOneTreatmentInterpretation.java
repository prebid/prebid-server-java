package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PurposeOneTreatmentInterpretation {

    ignore,
    @JsonProperty("no-access-allowed")
    noAccessAllowed,
    @JsonProperty("access-allowed")
    accessAllowed
}
