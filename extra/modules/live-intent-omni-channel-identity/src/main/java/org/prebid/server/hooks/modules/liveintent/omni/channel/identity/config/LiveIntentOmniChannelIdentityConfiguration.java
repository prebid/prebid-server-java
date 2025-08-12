package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.config;

import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.LiveIntentOmniChannelProperties;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.LiveIntentOmniChannelIdentityModule;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks.LiveIntentOmniChannelIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@ConditionalOnProperty(
        prefix = "hooks." + LiveIntentOmniChannelIdentityModule.CODE,
        name = "enabled",
        havingValue = "true")
public class LiveIntentOmniChannelIdentityConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + LiveIntentOmniChannelIdentityModule.CODE)
    LiveIntentOmniChannelProperties properties() {
        return new LiveIntentOmniChannelProperties();
    }

    @Bean
    Module liveIntentOmniChannelIdentityModule(LiveIntentOmniChannelProperties properties,
                                               JacksonMapper mapper,
                                               HttpClient httpClient,
                                               @Value("${logging.sampling-rate:0.01}") double logSamplingRate) {

        final LiveIntentOmniChannelIdentityProcessedAuctionRequestHook hook =
                new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(
                        properties, mapper, httpClient, () -> ThreadLocalRandom.current().nextLong(), logSamplingRate);

        return new LiveIntentOmniChannelIdentityModule(Collections.singleton(hook));
    }
}
