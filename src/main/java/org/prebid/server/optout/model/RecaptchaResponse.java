package org.prebid.server.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value
public class RecaptchaResponse {

    Boolean success;

    @JsonProperty("error-codes")
    List<String> errorCodes;
}
