package org.prebid.server.proto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.json.deserializer.CommaSeparatedStringToListOfIntegersDeserializer;

import java.util.List;
import java.util.Set;

@Builder(toBuilder = true)
@Value
public class CookieSyncRequest {

    Set<String> bidders;

    Integer gdpr;

    String gdprConsent;

    String usPrivacy;

    String gpp;

    @JsonDeserialize(using = CommaSeparatedStringToListOfIntegersDeserializer.class)
    List<Integer> gppSid;

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

