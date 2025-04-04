package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import lombok.AllArgsConstructor;
import org.prebid.server.hooks.modules.optable.targeting.model.net.OptableCall;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.json.JacksonMapper;

import java.util.Optional;

@AllArgsConstructor
public class OptableResponseParser {

    private final JacksonMapper mapper;

    public TargetingResult parse(OptableCall call) {
        return Optional.ofNullable(call)
                .map(OptableCall::getResponse)
                .map(resp -> mapper.decodeValue(resp.getBody(), TargetingResult.class))
                .orElse(null);
    }
}
