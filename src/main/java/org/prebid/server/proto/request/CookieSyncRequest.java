package org.prebid.server.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder
@Value
public class CookieSyncRequest {

    Set<String> bidders;

    Integer gdpr;

    String gdprConsent;

    String usPrivacy;

    String gpp;

    String gppSid;

    @JsonProperty("coopSync")
    Boolean coopSync;

    Boolean debug;

    Integer limit;

    String account;

    @JsonProperty("filterSettings")
    FilterSettings filterSettings;

    @Value(staticConstructor = "of")
    public static class FilterSettings {

        MethodFilter iframe;

        MethodFilter image;
    }

    @Value(staticConstructor = "of")
    public static class MethodFilter {

        JsonNode bidders;

        FilterType filter;
    }

    public enum FilterType {
        include, exclude
    }
}

