package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ThrottlingMessage {

    String browser;

    String bidder;

    String adUnitCode;

    String country;

    String hostname;

    String device;

    String hourBucket;

    String minuteQuadrant;
}
