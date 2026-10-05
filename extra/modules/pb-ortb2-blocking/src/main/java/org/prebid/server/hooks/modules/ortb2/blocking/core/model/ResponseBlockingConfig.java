package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.Map;

@Builder
@Value
public class ResponseBlockingConfig {

    BidAttributeBlockingConfig<String> badv;

    BidAttributeBlockingConfig<String> bcat;

    BidAttributeBlockingConfig<Integer> cattax;

    BidAttributeBlockingConfig<String> bapp;

    Map<MediaType, BidAttributeBlockingConfig<Integer>> battr;
}
