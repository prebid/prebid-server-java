package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ThrottlingMessage {

    Integer hourBucket;

    Integer minuteQuadrant;

    String bidder;

    String adUnitCode;

    String hostname;

    String browser;

    String country;

    String device;

    Boolean isPc;

    Boolean isMobile;

    Boolean isTablet;

    Boolean isBot;

    Boolean isTouchCapable;
}
