package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class OptoutRequest {

    String publisher;

    List<OptoutSlot> slots;

    CurrencySettings cur;

    String url;

    @JsonProperty("sdk_version")
    String sdkVersion;

    @JsonProperty("gdpr_consent")
    String gdprConsent;

    Integer gdpr;

    String os;

    Viewer viewer;

    Map<String, String> customs;

    String ortb2;
}
