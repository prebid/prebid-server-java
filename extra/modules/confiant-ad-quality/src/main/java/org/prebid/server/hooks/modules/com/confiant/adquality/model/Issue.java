package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * An object containing a spec_name,
 * a value and the ID of the first ad instance
 * where that issue was found for this creative
 */
@Data
public class Issue {

    @JsonProperty("spec_name")
    String specName;

    String value;

    @JsonProperty("first_adinstance")
    String firstAdinstance;
}
