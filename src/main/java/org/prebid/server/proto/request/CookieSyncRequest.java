package org.prebid.server.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class CookieSyncRequest {

    List<String> bidders;

    Integer gdpr;

    String gdprConsent;

    String usPrivacy;

    @JsonProperty("coopSync")
    Boolean coopSync;

    Integer limit;
}

