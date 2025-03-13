package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Value;

import java.util.List;

@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class TargetingResult {

    List<Audience> audience;

    Ortb2 ortb2;
}
