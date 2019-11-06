package com.iab.openrtb.request.video;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class Gdpr {

    @JsonProperty("consentrequired")
    Boolean consentRequired;

    @JsonProperty("consentstring")
    String consentString;
}

