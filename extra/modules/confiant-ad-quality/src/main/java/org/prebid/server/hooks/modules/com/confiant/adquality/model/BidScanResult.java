package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BidScanResult {

    /**
     * Unique reference to the creative tag found in the bid response.
     * Making the tag_key available on client side is required to allow Confiant
     * to sync the server side scan result with client side verification and by that close the loop
     * and improve overall detection. Please see “RTB Ad Verification Lifecycle” document for more info.
     */
    @JsonProperty("tag_key")
    String tagKey;

    /** Impression ID as retrieved in breq.imp.id */
    @JsonProperty("imp_id")
    String impId;

    /** An array of issue objects {@link Issue}. Only available if known_creative is true. */
    List<Issue> issues;
}
