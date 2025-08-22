package org.prebid.server.auction.externalortb;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountProfilesConfig;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProfilesProcessorTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private ApplicationSettings applicationSettings;

    @Mock(strictness = LENIENT)
    private TimeoutFactory timeoutFactory;

    @Mock(strictness = LENIENT)
    private Metrics metrics;

    private ProfilesProcessor target;

    @BeforeEach
    public void setUp() {
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(emptyMap(), emptyMap(), emptyList())));

        target = givenTarget(100, true);
    }

    @Test
    public void processShouldReturnSameBidRequestIfNoProfilesFound() {
        // given
        final AuctionContext auctionContext = givenAuctionContext();
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.result()).isSameAs(bidRequest);
        verifyNoInteractions(applicationSettings, timeoutFactory, metrics);
    }

    @Test
    public void processShouldFetchExpectedProfiles() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        identity(),
                        withProfiles(givenImp(identity()), "ip11", "ip12"),
                        withProfiles(givenImp(identity()), "ip21", "ip22"),
                        withProfiles(givenImp(identity()), "ip31", "ip32")),
                "rp1", "rp2", "rp3");

        // when
        target.process(auctionContext, bidRequest);

        // then
        assertThat(auctionContext.getDebugWarnings()).isEmpty();

        verify(applicationSettings).getProfiles(
                eq("accountId"),
                eq(Set.of("rp1", "rp2", "rp3")),
                eq(Set.of("ip11", "ip12", "ip21", "ip22", "ip31", "ip32")),
                any());
        verifyNoInteractions(metrics);
    }

    @Test
    public void processShouldLimitImpProfiles() {
        // given
        target = givenTarget(4, true);

        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        identity(),
                        withProfiles(givenImp(identity()), "ip11", "ip12"),
                        withProfiles(givenImp(identity())),
                        withProfiles(givenImp(identity()), "ip31")),
                "rp1", "rp2", "rp3");

        // when
        target.process(auctionContext, bidRequest);

        // then
        assertThat(auctionContext.getDebugWarnings()).containsExactly("Profiles exceeded the limit.");

        verify(applicationSettings).getProfiles(
                eq("accountId"),
                eq(Set.of("rp1", "rp2", "rp3")),
                eq(Set.of("ip11", "ip31")),
                any());
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.limit_exceeded));
    }

    @Test
    public void processShouldLimitRequestProfiles() {
        // given
        target = givenTarget(2, true);

        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        identity(),
                        withProfiles(givenImp(identity()), "ip11", "ip12"),
                        withProfiles(givenImp(identity()), "ip21", "ip22"),
                        withProfiles(givenImp(identity()), "ip31", "ip32")),
                "rp1", "rp2", "rp3");

        // when
        target.process(auctionContext, bidRequest);

        // then
        assertThat(auctionContext.getDebugWarnings()).containsExactly("Profiles exceeded the limit.");

        verify(applicationSettings).getProfiles(
                eq("accountId"),
                eq(Set.of("rp1", "rp2")),
                eq(emptySet()),
                any());
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.limit_exceeded));
    }

    @Test
    public void processShouldLimitRequestProfilesUsingAccountConfig() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(AccountProfilesConfig.of(2, null), true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        identity(),
                        withProfiles(givenImp(identity()), "ip11", "ip12"),
                        withProfiles(givenImp(identity()), "ip21", "ip22"),
                        withProfiles(givenImp(identity()), "ip31", "ip32")),
                "rp1", "rp2", "rp3");

        // when
        target.process(auctionContext, bidRequest);

        // then
        assertThat(auctionContext.getDebugWarnings()).containsExactly("Profiles exceeded the limit.");

        verify(applicationSettings).getProfiles(
                eq("accountId"),
                eq(Set.of("rp1", "rp2")),
                eq(emptySet()),
                any());
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.limit_exceeded));
    }

    @Test
    public void processShouldFailAndEmitMetricsWhenDebugDisabledAndFailOnUnknownIsTrue() {
        // given
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of("rp1", Profile.of(
                                Profile.Type.REQUEST,
                                Profile.MergePrecedence.REQUEST,
                                mapper.createObjectNode())),
                        emptyMap(),
                        singletonList("rp2 missed"))));

        final AuctionContext auctionContext = givenAuctionContext(false);
        final BidRequest bidRequest = withProfiles(givenBidRequest(identity()), "rp1", "rp2");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo("Error during processing profiles: rp2 missed");

        verify(metrics).updateProfileMetric(eq(MetricName.missing));
    }

    @Test
    public void processShouldFailAndEmitMetricsWhenDebugEnabledAndFailOnUnknownIsTrue() {
        // given
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of("rp1", Profile.of(
                                Profile.Type.REQUEST,
                                Profile.MergePrecedence.REQUEST,
                                mapper.createObjectNode())),
                        emptyMap(),
                        singletonList("rp2 missed"))));

        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(givenBidRequest(identity()), "rp1", "rp2");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo("Error during processing profiles: rp2 missed");
        assertThat(auctionContext.getDebugWarnings()).containsExactly("rp2 missed");

        verify(metrics).updateProfileMetric(eq(MetricName.missing));
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.missing));
    }

    @Test
    public void processShouldIgnoreErrorsAndEmitMetricsWhenDebugEnabledAndFailOnUnknownIsFalse() {
        // given
        target = givenTarget(100, false);

        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of("rp1", Profile.of(
                                Profile.Type.REQUEST,
                                Profile.MergePrecedence.REQUEST,
                                mapper.createObjectNode())),
                        emptyMap(),
                        singletonList("rp2 missed"))));

        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(givenBidRequest(identity()), "rp1", "rp2");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(auctionContext.getDebugWarnings()).containsExactly("rp2 missed");

        verify(metrics).updateProfileMetric(eq(MetricName.missing));
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.missing));
    }

    @Test
    public void processShouldIgnoreErrorsUsingAccountConfig() {
        // given
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of("rp1", Profile.of(
                                Profile.Type.REQUEST,
                                Profile.MergePrecedence.REQUEST,
                                mapper.createObjectNode())),
                        emptyMap(),
                        singletonList("rp2 missed"))));

        final AuctionContext auctionContext = givenAuctionContext(AccountProfilesConfig.of(100, false), true);
        final BidRequest bidRequest = withProfiles(givenBidRequest(identity()), "rp1", "rp2");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(auctionContext.getDebugWarnings()).containsExactly("rp2 missed");

        verify(metrics).updateProfileMetric(eq(MetricName.missing));
        verify(metrics).updateAccountProfileMetric(eq("accountId"), eq(MetricName.missing));
    }

    @Test
    public void processShouldFailOnInvalidProfileIfFailOnUnknown() {
        // given
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of(
                                "rp1", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.REQUEST,
                                        mapper.valueToTree(givenBidRequest(request -> request.test(1).at(1)))),
                                "rp2", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.PROFILE,
                                        TextNode.valueOf("invalid")),
                                "rp3", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.PROFILE,
                                        mapper.valueToTree(givenBidRequest(request -> request.id("newRequestId"))))),
                        emptyMap(),
                        emptyList())));

        final AuctionContext auctionContext = givenAuctionContext(true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        request -> request.id("requestId").test(0),
                        withProfiles(givenImp(identity()), "ip11", "ip12"),
                        withProfiles(givenImp(identity()), "ip21", "ip22"),
                        withProfiles(givenImp(identity()), "ip31", "ip32")),
                "rp1", "rp2", "rp3");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause().getMessage()).isEqualTo("""
                Error during processing profiles: \
                Can't merge with profile rp2: \
                One of the merge arguments is not an object.""");

        verify(metrics).updateProfileMetric(eq(MetricName.invalid));
    }

    @Test
    public void processShouldReturnExpectedResult() {
        // given
        given(applicationSettings.getProfiles(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredDataResult.of(
                        Map.of(
                                "rp1", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.REQUEST,
                                        mapper.valueToTree(givenBidRequest(request -> request.test(1).at(1)))),
                                "rp2", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.PROFILE,
                                        TextNode.valueOf("invalid")),
                                "rp3", Profile.of(
                                        Profile.Type.REQUEST,
                                        Profile.MergePrecedence.PROFILE,
                                        mapper.valueToTree(givenBidRequest(request -> request.id("newRequestId"))))),
                        Map.of(
                                "ip11", Profile.of(
                                        Profile.Type.IMP,
                                        Profile.MergePrecedence.REQUEST,
                                        mapper.valueToTree(givenImp(imp -> imp.id("id11")))),
                                "ip12", Profile.of(
                                        Profile.Type.IMP,
                                        Profile.MergePrecedence.REQUEST,
                                        TextNode.valueOf("invalid")),
                                "ip13", Profile.of(
                                        Profile.Type.IMP,
                                        Profile.MergePrecedence.REQUEST,
                                        mapper.valueToTree(givenImp(imp -> imp.id("id13")))),
                                "ip21", Profile.of(
                                        Profile.Type.IMP,
                                        Profile.MergePrecedence.PROFILE,
                                        mapper.valueToTree(givenImp(imp -> imp.id("id21")))),
                                "ip22", Profile.of(
                                        Profile.Type.IMP,
                                        Profile.MergePrecedence.PROFILE,
                                        mapper.valueToTree(givenImp(imp -> imp.id("id22"))))),
                        emptyList())));

        final AuctionContext auctionContext = givenAuctionContext(AccountProfilesConfig.of(100, false), true);
        final BidRequest bidRequest = withProfiles(
                givenBidRequest(
                        request -> request.id("requestId").test(0),
                        withProfiles(givenImp(identity()), "ip11", "ip12", "ip13"),
                        withProfiles(givenImp(identity()), "ip21", "ip22")),
                "rp1", "rp2", "rp3");

        // when
        final Future<BidRequest> result = target.process(auctionContext, bidRequest);

        // then
        assertThat(result.result()).satisfies(request -> {
            assertThat(request.getId()).isEqualTo("newRequestId");
            assertThat(request.getTest()).isEqualTo(0);
            assertThat(request.getAt()).isEqualTo(1);

            assertThat(request.getImp())
                    .extracting(Imp::getId)
                    .containsExactly("id11", "id22");
        });

        verify(metrics, times(2)).updateProfileMetric(eq(MetricName.invalid));
    }

    private ProfilesProcessor givenTarget(int maxProfiles, boolean failOnUnknown) {
        return new ProfilesProcessor(
                maxProfiles,
                100L,
                failOnUnknown,
                0.0,
                applicationSettings,
                timeoutFactory,
                metrics,
                jacksonMapper,
                new JsonMerger(jacksonMapper));
    }

    private static AuctionContext givenAuctionContext() {
        return givenAuctionContext(false);
    }

    private static AuctionContext givenAuctionContext(boolean debugEnabled) {
        return givenAuctionContext(null, debugEnabled);
    }

    private static AuctionContext givenAuctionContext(AccountProfilesConfig profilesConfig, boolean debugEnabled) {
        return AuctionContext.builder()
                .account(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .profiles(profilesConfig)
                                .build())
                        .build())
                .debugContext(DebugContext.of(debugEnabled, false, null))
                .debugWarnings(new ArrayList<>())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(List.of(imps))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static BidRequest withProfiles(BidRequest bidRequest, String... profiles) {
        return bidRequest.toBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .profiles(List.of(profiles))
                        .build()))
                .build();
    }

    private static Imp withProfiles(Imp imp, String... profiles) {
        return imp.toBuilder()
                .ext(mapper.valueToTree(ExtImp.of(
                        ExtImpPrebid.builder()
                                .profiles(List.of(profiles))
                                .build(),
                        null)))
                .build();
    }
}
