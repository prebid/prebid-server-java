package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ThrottlingMessage {

    String browser; // ok

    String bidder; // ok

    String adUnitCode; // ok

    String country; // ok

    String hostname; // ok

    String device; // ok

    //Boolean isPc;

    String isMobile; // ok

    String isTablet; // ok

    //Boolean isBot;

    //Boolean isTouchCapable;

    String hourBucket;

    String minuteQuadrant;
}
