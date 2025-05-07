package org.prebid.server.hooks.modules.liveintent.omni.channel.identity.config;

import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.LiveIntentOmniChannelIdentityModule;
import org.prebid.server.hooks.modules.liveintent.omni.channel.identity.v1.hooks.LiveIntentOmniChannelIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.Module;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "hooks." + LiveIntentOmniChannelIdentityModule.CODE, name = "enabled", havingValue = "true")
public class LiveIntentOmniChannelIdentityConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + LiveIntentOmniChannelIdentityModule.CODE)
    ModuleConfig moduleConfig() {
        return new ModuleConfig();
    }

    @Bean
    Module liveIntentOmniChannelIdentityModule(ModuleConfig config, JacksonMapper mapper, HttpClient httpClient) {
        final Set<? extends Hook<?, ? extends InvocationContext>> hooks = Set.of(
                new LiveIntentOmniChannelIdentityProcessedAuctionRequestHook(config, mapper, httpClient));
        return new LiveIntentOmniChannelIdentityModule(hooks);
    }
}
