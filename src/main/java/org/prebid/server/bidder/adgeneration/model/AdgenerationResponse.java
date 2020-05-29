package org.prebid.server.bidder.adgeneration.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class AdgenerationResponse<T> {

    String locationid;

    String dealid;

    String ad;

    String beacon;

    String baconurl;

    BigDecimal cpm;

    String creativeid;

    Integer h;

    Integer w;

    Integer ttl;

    String vastxml;

    String landingUrl;

    String scheduleid;

    T results;
}
