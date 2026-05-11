package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderEnrichmentDicer;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(VertxExtension.class)
public class OptableRawAuctionRequestHookTest extends BaseOptableTest {

    @Mock
    private OptableTargeting optableTargeting;
    @Mock
    private UserFpdActivityMask userFpdActivityMask;
    @Mock
    private AuctionRequestPayload auctionRequestPayload;
    @Mock
    private ActivityInfrastructure activityInfrastructure;
    @Mock
    private AuctionInvocationContext invocationContext;
    @Mock
    private Timeout timeout;
    @Mock
    private BidderEnrichmentDicer bidderEnrichmentDicer;

    private ConfigResolver configResolver;
    private NetworkCall networkCall;
    private OptableRawAuctionRequestHook target;

    @BeforeEach
    public void setUp() {
        when(userFpdActivityMask.maskDevice(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(answer -> answer.getArgument(0));
        configResolver = new ConfigResolver(mapper, jsonMerger, givenOptableTargetingProperties(false));
        networkCall = new NetworkCall(optableTargeting, userFpdActivityMask);
        target = new OptableRawAuctionRequestHook(configResolver, networkCall, bidderEnrichmentDicer, 0.01);
        when(invocationContext.auctionContext()).thenReturn(givenAuctionContext(activityInfrastructure, timeout));
        when(invocationContext.timeout()).thenReturn(timeout);
        when(activityInfrastructure.isAllowed(any(), any())).thenReturn(true);
        when(timeout.remaining()).thenReturn(1000L);
    }

    @Test
    public void shouldHaveRightCode() {
        // when and then
        assertThat(target.code()).isEqualTo("optable-targeting-raw-auction-request-hook");
    }

    @SneakyThrows
    @Test
    public void shouldInjectEarlyNetworkCallToModuleContext(VertxTestContext vertxTestContext) {
        // given
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", "tenant", "origin", true));
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));
        when(bidderEnrichmentDicer.dice(any(), any())).thenReturn(Set.of("bidder"));

        // when
        final Future<InvocationResult<AuctionRequestPayload>> result =
                target.call(auctionRequestPayload, invocationContext);

        // then
        assertThat(result).isNotNull();
        result.map(res -> (ModuleContext) res.moduleContext())
                .compose(ModuleContext::getOptableTargetingCall)
                .onComplete(call -> {
                    vertxTestContext.verify(() -> {
                        assertThat(call.result()).isNotNull();
                    });
                    vertxTestContext.completeNow();
                });
    }

    @SneakyThrows
    @Test
    public void shouldNotInjectEarlyNetworkCallToModuleContextWhenOriginIsAbsentInAccountConfiguration(
            VertxTestContext vertxTestContext) {

        // given
        when(invocationContext.accountConfig())
                .thenReturn(givenAccountConfig("key", "tenant", null, true));
        when(auctionRequestPayload.bidRequest()).thenReturn(givenBidRequest());
        when(optableTargeting.getTargeting(any(), any(), any(), any()))
                .thenReturn(Future.succeededFuture(givenTargetingResult()));

        configResolver = new ConfigResolver(
                mapper, jsonMerger, givenOptableTargetingProperties("key", "tenant", null, true));
        target = new OptableRawAuctionRequestHook(configResolver, networkCall, bidderEnrichmentDicer, 0.01);

        // when
        final Future<InvocationResult<AuctionRequestPayload>> result =
                target.call(auctionRequestPayload, invocationContext);

        // then
        assertThat(result).isNotNull();
        result.map(res -> (ModuleContext) res.moduleContext())
                .onComplete(cxt -> {
                    vertxTestContext.verify(() -> {
                        final ModuleContext moduleContext = cxt.result();
                        assertThat(moduleContext.getOptableTargetingCall()).isNull();
                        assertThat(moduleContext.isEarlyNetworkCallEnabled()).isTrue();
                    });
                    vertxTestContext.completeNow();
                });
    }
}
