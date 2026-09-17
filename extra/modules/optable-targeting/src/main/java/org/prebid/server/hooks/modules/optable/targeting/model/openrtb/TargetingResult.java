package org.prebid.server.hooks.modules.optable.targeting.model.openrtb;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.List;

@Value
public class TargetingResult {

    List<Audience> audience;

    Ortb2 ortb2;

    ObjectNode refs;
}
