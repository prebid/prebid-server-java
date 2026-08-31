package org.prebid.server.hooks.modules.intentiq.identity.v1;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.FirstPartyKeyExtractor;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;

import java.util.List;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class IntentiqIdentityModuleTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());

    @Mock
    private HttpClient httpClient;

    @Test
    public void codeShouldReturnExpectedValue() {
        // given
        final IntentiqIdentityModule target = new IntentiqIdentityModule(emptyList());

        // when and then
        assertThat(target.code()).isEqualTo("intentiq-identity");
    }

    @Test
    public void hooksShouldReturnProvidedHooks() {
        // given
        final ConfigResolver configResolver =
                new ConfigResolver(MAPPER.mapper(), new JsonMerger(MAPPER), new IntentiqIdentityProperties());
        final List<? extends Hook<?, ? extends InvocationContext>> hooks = List.of(
                new IntentiqIdentityProcessedAuctionRequestHook(
                        configResolver, httpClient, MAPPER, null,
                        new FirstPartyKeyExtractor(10), new IntentiqIdentityMetrics(new MetricRegistry())));
        final IntentiqIdentityModule target = new IntentiqIdentityModule(hooks);

        // when and then
        assertThat(target.hooks()).isEqualTo(hooks);
    }
}
