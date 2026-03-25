package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RedisError {

    String message;

    @JsonProperty("dsp_id")
    String dspId;

    boolean error;

    String code;
}
