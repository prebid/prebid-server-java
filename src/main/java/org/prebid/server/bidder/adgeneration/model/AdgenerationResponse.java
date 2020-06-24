package org.prebid.server.bidder.adgeneration.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class AdgenerationResponse {

    String locationid;

    String dealid;

    String ad;

    String beacon;

    @JsonProperty("baconurl")
    String baconUrl;

    BigDecimal cpm;

    String creativeid;

    Integer h;

    Integer w;

    Integer ttl;

    String vastxml;

    String landingUrl;

    String scheduleid;

    List<ObjectNode> results;
}
