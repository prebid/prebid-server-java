package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record OptoutRequest(String publisher,
                            List<OptoutSlot> slots,
                            CurrencySettings cur,
                            String url,
                            @JsonProperty("sdk_version") String sdkVersion,
                            @JsonProperty("gdpr_onsent") String gdprConsent,
                            int gdpr,
                            String os,
                            Viewer viewer,
                            Map<String, String> customs,
                            String ortb2) {
}
