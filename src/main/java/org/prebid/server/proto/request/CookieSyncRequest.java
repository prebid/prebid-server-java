package org.prebid.server.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class CookieSyncRequest {

    List<String> bidders;

    Integer gdpr;

    String gdprConsent;

    String usPrivacy;

    @JsonProperty("coopSync")
    Boolean coopSync;

    Integer limit;

    String account;
}

