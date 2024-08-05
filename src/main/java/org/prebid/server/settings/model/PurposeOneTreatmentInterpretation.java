package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum PurposeOneTreatmentInterpretation {

    ignore,

    @JsonProperty("no_access_allowed")
    @JsonAlias("no-access-allowed")
    noAccessAllowed,

    @JsonProperty("access_allowed")
    @JsonAlias("access-allowed")
    accessAllowed
}
