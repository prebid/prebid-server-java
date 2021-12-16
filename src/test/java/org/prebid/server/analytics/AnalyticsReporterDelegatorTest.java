package org.prebid.server.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AnalyticsReporterDelegatorTest {

    private static final String EVENT = StringUtils.EMPTY;
    private static final Integer FIRST_REPORTER_ID = 1;
    private static final Integer SECOND_REPORTER_ID = 2;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private Metrics metrics;

    private AnalyticsReporter firstReporter;

    private AnalyticsReporter secondReporter;

    private AnalyticsReporterDelegator target;

    @Before
    public void setUp() {
        firstReporter = mock(AnalyticsReporter.class);
        given(firstReporter.vendorId()).willReturn(FIRST_REPORTER_ID);
        given(firstReporter.name()).willReturn("logAnalytics");
        given(firstReporter.processEvent(any())).willReturn(Future.succeededFuture());

        secondReporter = mock(AnalyticsReporter.class);
        given(secondReporter.vendorId()).willReturn(SECOND_REPORTER_ID);
        given(secondReporter.name()).willReturn("adapter");
        given(secondReporter.processEvent(any())).willReturn(Future.succeededFuture());

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());
        final Map<Integer, PrivacyEnforcementAction> enforcementActionMap = new HashMap<>();
        enforcementActionMap.put(SECOND_REPORTER_ID, PrivacyEnforcementAction.allowAll());
        enforcementActionMap.put(FIRST_REPORTER_ID, PrivacyEnforcementAction.allowAll());
        given(privacyEnforcementService.resultForVendorIds(any(), any()))
                .willReturn(Future.succeededFuture(enforcementActionMap));

        target = new AnalyticsReporterDelegator(asList(firstReporter, secondReporter), vertx,
                privacyEnforcementService, metrics);
    }

    @Test
    public void shouldPassEventToAllDelegates() {
        // given
        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());

        // when
        target.processEvent(EVENT);

        // then
        verify(vertx, times(2)).runOnContext(any());
        assertThat(captureEvent(firstReporter)).isSameAs(EVENT);
        assertThat(captureEvent(secondReporter)).isSameAs(EVENT);
    }

    @Test
    public void shouldTolerateInvalidExtPrebidAnalyticsNode() {
        // given
        final TextNode analyticsNode = new TextNode("invalid");
        final AuctionEvent givenAuctionEvent = givenAuctionEvent(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .analytics(analyticsNode)
                        .build())));

        // when
        target.processEvent(givenAuctionEvent, TcfContext.empty());

        // then
        verify(vertx, times(2)).runOnContext(any());
        assertThat(asList(captureAuctionEvent(firstReporter), captureAuctionEvent(secondReporter)))
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .containsExactly(analyticsNode, analyticsNode);
    }

    @Test
    public void shouldTolerateWithMissingBidRequest() {
        // given
        final AuctionEvent givenAuctionEventWithoutContext = AuctionEvent.builder().build();
        final AuctionEvent givenAuctionEventWithoutBidRequest = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder().build())
                .build();

        // when
        target.processEvent(givenAuctionEventWithoutContext, TcfContext.empty());
        target.processEvent(givenAuctionEventWithoutBidRequest, TcfContext.empty());

        // then
        verify(vertx, times(4)).runOnContext(any());
    }

    @Test
    public void shouldPassOnlyAdapterRelatedEntriesToAnalyticReporters() {
        // given
        final ObjectNode analyticsNode = new ObjectMapper().createObjectNode();
        analyticsNode.set("adapter", new TextNode("someValue"));
        analyticsNode.set("anotherAdapter", new IntNode(2));
        final AuctionEvent givenAuctionEvent = givenAuctionEvent(bidRequestBuilder ->
                bidRequestBuilder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .analytics(analyticsNode)
                        .build())));

        // when
        target.processEvent(givenAuctionEvent, TcfContext.empty());

        // then
        verify(vertx, times(2)).runOnContext(any());
        assertThat(singleton(captureAuctionEvent(firstReporter)))
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .containsExactly(analyticsNode);
        final ObjectNode expectedAnalytics = new ObjectMapper().createObjectNode();
        expectedAnalytics.set("adapter", new TextNode("someValue"));
        assertThat(singleton(captureAuctionEvent(secondReporter)))
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .containsExactly(expectedAnalytics);
    }

    @Test
    public void shouldUpdateOkMetricsWithSpecificEventAndAdapterType() {
        // when
        target.processEvent(givenAuctionEvent(identity()), TcfContext.empty());

        // then
        verify(metrics).updateAnalyticEventMetric("logAnalytics", MetricName.event_auction, MetricName.ok);
        verify(metrics).updateAnalyticEventMetric("adapter", MetricName.event_auction, MetricName.ok);
    }

    @Test
    public void shouldUpdateTimeoutMetricsWithSpecificEventAndAdapterType() {
        // given
        given(firstReporter.processEvent(any())).willReturn(Future.failedFuture(new TimeoutException()));
        given(secondReporter.processEvent(any())).willReturn(Future.failedFuture(new ConnectTimeoutException()));

        // when
        target.processEvent(givenAuctionEvent(identity()), TcfContext.empty());

        // then
        verify(metrics).updateAnalyticEventMetric("logAnalytics", MetricName.event_auction, MetricName.timeout);
        verify(metrics).updateAnalyticEventMetric("adapter", MetricName.event_auction, MetricName.timeout);
    }

    @Test
    public void shouldUpdateErrorMetricsWithSpecificEventAndAdapterType() {
        // given
        given(firstReporter.processEvent(any())).willReturn(Future.failedFuture(new RuntimeException()));
        given(secondReporter.processEvent(any())).willReturn(Future.failedFuture(new Exception()));

        // when
        target.processEvent(givenAuctionEvent(identity()), TcfContext.empty());

        // then
        verify(metrics).updateAnalyticEventMetric("logAnalytics", MetricName.event_auction, MetricName.err);
        verify(metrics).updateAnalyticEventMetric("adapter", MetricName.event_auction, MetricName.err);
    }

    @Test
    public void shouldUpdateInvalidRequestMetricsWhenFutureContainsInvalidRequestException() {
        // given
        given(firstReporter.processEvent(any())).willReturn(Future.failedFuture(new InvalidRequestException("cause")));
        given(secondReporter.processEvent(any())).willReturn(Future.failedFuture(new InvalidRequestException("cause")));

        // when
        target.processEvent(givenAuctionEvent(identity()), TcfContext.empty());

        // then
        verify(metrics).updateAnalyticEventMetric("logAnalytics", MetricName.event_auction, MetricName.badinput);
        verify(metrics).updateAnalyticEventMetric("adapter", MetricName.event_auction, MetricName.badinput);
    }

    @Test
    public void shouldPassEventToAllowedDelegatesWhenSomeVendorIdWasAllowed() {
        // given
        final Map<Integer, PrivacyEnforcementAction> enforcementActionMap = new HashMap<>();
        enforcementActionMap.put(FIRST_REPORTER_ID, PrivacyEnforcementAction.restrictAll());
        enforcementActionMap.put(SECOND_REPORTER_ID, PrivacyEnforcementAction.allowAll());

        given(privacyEnforcementService.resultForVendorIds(any(), any()))
                .willReturn(Future.succeededFuture(enforcementActionMap));

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());

        // when
        target.processEvent(EVENT, TcfContext.empty());

        // then
        verify(vertx).runOnContext(any());
        assertThat(captureEvent(secondReporter)).isSameAs(EVENT);
    }

    @Test
    public void shouldNotPassEventToDelegatesWhenAllVendorIdsWasBlocked() {
        // given
        final Map<Integer, PrivacyEnforcementAction> enforcementActionMap = new HashMap<>();
        enforcementActionMap.put(FIRST_REPORTER_ID, PrivacyEnforcementAction.restrictAll());
        enforcementActionMap.put(SECOND_REPORTER_ID, PrivacyEnforcementAction.restrictAll());

        given(privacyEnforcementService.resultForVendorIds(any(), any()))
                .willReturn(Future.succeededFuture(enforcementActionMap));

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());

        // when
        target.processEvent(EVENT, TcfContext.empty());

        // then
        verify(vertx, never()).runOnContext(any());
    }

    @SuppressWarnings("unchecked")
    private static Answer<Object> withNullAndInvokeHandler() {
        return invocation -> {
            ((Handler<Void>) invocation.getArgument(0)).handle(null);
            return null;
        };
    }

    private static String captureEvent(AnalyticsReporter reporter) {
        final ArgumentCaptor<String> auctionEventCaptor = ArgumentCaptor.forClass(String.class);
        verify(reporter).processEvent(auctionEventCaptor.capture());
        return auctionEventCaptor.getValue();
    }

    private static AuctionEvent captureAuctionEvent(AnalyticsReporter reporter) {
        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(reporter).processEvent(auctionEventCaptor.capture());
        return auctionEventCaptor.getValue();
    }

    private static AuctionEvent givenAuctionEvent(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        return AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .bidRequest(bidRequestCustomizer.apply(BidRequest.builder()).build())
                        .build())
                .build();
    }
}
