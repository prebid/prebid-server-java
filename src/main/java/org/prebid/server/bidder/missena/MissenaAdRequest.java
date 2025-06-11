package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.SupplyChain;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class MissenaAdRequest {

    @JsonProperty("adunit")
    String adUnit;

    @JsonProperty("buyeruid")
    String buyerUid;

    Integer coppa;

    String currency;

    @JsonProperty("userEids")
    List<Eid> userEids;

    BigDecimal floor;

    String floorCurrency;

    @JsonProperty("consent_required")
    Boolean gdpr;

    @JsonProperty("consent_string")
    String gdprConsent;

    @JsonProperty("ik")
    String idempotencyKey;

    String referer;

    String refererCanonical;

    String requestId;

    SupplyChain schain;

    Long timeout;

    String url;

    MissenaUserParams params;

    String usPrivacy;

    String version;
}
