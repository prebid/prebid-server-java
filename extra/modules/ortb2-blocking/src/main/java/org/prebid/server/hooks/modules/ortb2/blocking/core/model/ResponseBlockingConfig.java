package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ResponseBlockingConfig {

    BidAttributeBlockingConfig<String> badv;

    BidAttributeBlockingConfig<String> bcat;

    BidAttributeBlockingConfig<String> bapp;

    BidAttributeBlockingConfig<Integer> battr;
}
