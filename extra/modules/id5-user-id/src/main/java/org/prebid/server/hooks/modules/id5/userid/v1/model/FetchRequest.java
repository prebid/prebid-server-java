package org.prebid.server.hooks.modules.id5.userid.v1.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.modules.id5.userid.v1.config.Id5IdModuleProperties;

import java.util.List;

@Builder
@Value
public class FetchRequest {

    @JsonProperty("partner")
    long partnerId;

    @JsonProperty("ts")
    String timestamp;

    @JsonAlias("ip")
    String ipv4;

    String ipv6;

    @JsonProperty("ua")
    String userAgent;

    String maid;

    String domain;

    String ref;

    String bundle;

    String att;

    String provider;

    PrebidServerMetadata providerMetadata;

    String origin;

    String version;

    @JsonProperty("us_privacy")
    String usPrivacy;

    String gdpr;

    @JsonProperty("gdpr_consent")
    String gdprConsent;

    @JsonProperty("gpp_string")
    String gppString;

    @JsonProperty("gpp_sid")
    String gppSid;

    String coppa;

    @JsonProperty("_trace")
    boolean trace;

    @Builder
    @Value
    public static class PrebidServerMetadata {
        String channel;
        String channelVersion;
        Id5IdModuleProperties id5ModuleConfig;
        Publisher publisher;
        List<String> bidders;
    }

    @Builder
    @Value
    public static class Publisher {
        String id;
        String name;
        String domain;
    }
}
