package org.prebid.server.analytics.reporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.privacy.enforcement.TcfEnforcement;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AnalyticsReporterDelegatorTest extends VertxTest {

    private static final String EVENT = StringUtils.EMPTY;
    private static final Integer FIRST_REPORTER_ID = 1;
    private static final Integer SECOND_REPORTER_ID = 2;

    @Mock(strictness = LENIENT)
    private Vertx vertx;
    @Mock(strictness = LENIENT)
    private TcfEnforcement tcfEnforcement;
    @Mock
    private UserFpdActivityMask userFpdActivityMask;
    @Mock
    private Metrics metrics;
    @Mock(strictness = LENIENT)
    private AnalyticsReporter firstReporter;
    @Mock(strictness = LENIENT)
    private AnalyticsReporter secondReporter;

    private AnalyticsReporterDelegator target;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @BeforeEach
    public void setUp() {
        given(firstReporter.vendorId()).willReturn(FIRST_REPORTER_ID);
        given(firstReporter.name()).willReturn("logAnalytics");
        given(firstReporter.processEvent(any())).willReturn(Future.succeededFuture());

        given(secondReporter.vendorId()).willReturn(SECOND_REPORTER_ID);
        given(secondReporter.name()).willReturn("adapter");
        given(secondReporter.processEvent(any())).willReturn(Future.succeededFuture());

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());
        final Map<Integer, PrivacyEnforcementAction> enforcementActionMap = new HashMap<>();
        enforcementActionMap.put(SECOND_REPORTER_ID, PrivacyEnforcementAction.allowAll());
        enforcementActionMap.put(FIRST_REPORTER_ID, PrivacyEnforcementAction.allowAll());
        given(tcfEnforcement.enforce(any(), any()))
                .willReturn(Future.succeededFuture(enforcementActionMap));

        target = new AnalyticsReporterDelegator(
                vertx,
                List.of(firstReporter, secondReporter),
                tcfEnforcement,
                userFpdActivityMask,
                metrics,
                0.01,
                Set.of("logAnalytics", "adapter"),
                jacksonMapper);
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

        given(tcfEnforcement.enforce(any(), any()))
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

        given(tcfEnforcement.enforce(any(), any()))
                .willReturn(Future.succeededFuture(enforcementActionMap));

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());

        // when
        target.processEvent(EVENT, TcfContext.empty());

        // then
        verify(vertx, never()).runOnContext(any());
    }

    @Test
    public void shouldNotPassAuctionEventToDisallowedDelegates() {
        // given
        given(activityInfrastructure.isAllowed(
                eq(Activity.REPORT_ANALYTICS),
                argThat(argument -> argument.componentType().equals(ComponentType.ANALYTICS))))
                .willReturn(false);

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .activityInfrastructure(activityInfrastructure)
                        .build())
                .build();

        // when
        target.processEvent(auctionEvent, TcfContext.empty());

        // then
        verify(vertx, never()).runOnContext(any());
    }

    @Test
    public void shouldNotPassAmpEventToDisallowedDelegates() {
        // given
        given(activityInfrastructure.isAllowed(
                eq(Activity.REPORT_ANALYTICS),
                argThat(argument -> argument.componentType().equals(ComponentType.ANALYTICS))))
                .willReturn(false);

        final AmpEvent ampEvent = AmpEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .activityInfrastructure(activityInfrastructure)
                        .build())
                .build();

        // when
        target.processEvent(ampEvent, TcfContext.empty());

        // then
        verify(vertx, never()).runOnContext(any());
    }

    @Test
    public void shouldNotPassNotificationEventToDisallowedDelegates() {
        // given
        given(activityInfrastructure.isAllowed(
                eq(Activity.REPORT_ANALYTICS),
                argThat(argument -> argument.componentType().equals(ComponentType.ANALYTICS))))
                .willReturn(false);

        final NotificationEvent notificationEvent = NotificationEvent.builder()
                .activityInfrastructure(activityInfrastructure)
                .build();

        // when
        target.processEvent(notificationEvent);

        // then
        verify(vertx, never()).runOnContext(any());
    }

    @Test
    public void shouldUpdateAuctionEventToConsideringActivitiesRestrictions() {
        // given
        given(activityInfrastructure.isAllowed(eq(Activity.REPORT_ANALYTICS), any())).willReturn(true);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_UFPD), any())).willReturn(false);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_EIDS), any())).willReturn(false);
        given(activityInfrastructure.isAllowed(eq(Activity.TRANSMIT_GEO), any())).willReturn(false);

        given(userFpdActivityMask.maskUser(any(), eq(true), eq(true)))
                .willReturn(User.builder().id("masked").build());
        given(userFpdActivityMask.maskDevice(any(), eq(true), eq(true)))
                .willReturn(Device.builder().model("masked").build());

        final AuctionEvent auctionEvent = AuctionEvent.builder()
                .auctionContext(AuctionContext.builder()
                        .bidRequest(BidRequest.builder()
                                .user(User.builder().id("original").build())
                                .device(Device.builder().model("original").build())
                                .build())
                        .activityInfrastructure(activityInfrastructure)
                        .build())
                .build();

        // when
        target.processEvent(auctionEvent, TcfContext.empty());

        // then
        verify(vertx, times(2)).runOnContext(any());

        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(secondReporter).processEvent(auctionEventCaptor.capture());
        assertThat(auctionEventCaptor.getValue())
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .satisfies(bidRequest -> {
                    assertThat(bidRequest.getUser())
                            .extracting(User::getId)
                            .isEqualTo("masked");

                    assertThat(bidRequest.getDevice())
                            .extracting(Device::getModel)
                            .isEqualTo("masked");
                });
    }

    @Test
    public void shouldNotCallAnalyticsAdapterIfDisabledByAccount() {
        // given
        final ObjectNode moduleConfig = mapper.createObjectNode();
        moduleConfig.put("enabled", false);
        moduleConfig.put("property1", "value1");
        moduleConfig.put("property2", "value2");

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .analytics(AccountAnalyticsConfig.of(
                                true, null, Map.of("logAnalytics", moduleConfig)))
                        .build())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().analytics(mapper.createObjectNode()).build()))
                        .build())
                .build();

        // when
        target.processEvent(AuctionEvent.builder().auctionContext(auctionContext).build());

        // then
        verify(vertx).runOnContext(any());
        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(firstReporter, never()).processEvent(auctionEventCaptor.capture());
    }

    @Test
    public void shouldUpdateAuctionEventWithPropertiesFromAdapterSpecificAccountConfig() {
        // given
        final ObjectNode moduleConfig = mapper.createObjectNode();
        moduleConfig.put("enabled", true);
        moduleConfig.put("property1", "value1");
        moduleConfig.put("property2", "value2");

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .analytics(AccountAnalyticsConfig.of(
                                true, null, Map.of("logAnalytics", moduleConfig)))
                        .build())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().analytics(mapper.createObjectNode()).build()))
                        .build())
                .build();

        // when
        target.processEvent(AuctionEvent.builder().auctionContext(auctionContext).build());

        // then
        verify(vertx, times(2)).runOnContext(any());

        final ObjectNode expectedAnalyticsNode = mapper.createObjectNode();
        final ObjectNode expectedLogAnalyticsNode = mapper.createObjectNode();
        expectedLogAnalyticsNode.put("property1", "value1");
        expectedLogAnalyticsNode.put("property2", "value2");
        expectedAnalyticsNode.set("logAnalytics", expectedLogAnalyticsNode);

        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(firstReporter).processEvent(auctionEventCaptor.capture());
        assertThat(auctionEventCaptor.getValue())
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .isEqualTo(expectedAnalyticsNode);
    }

    @Test
    public void shouldUpdateAuctionEventWithPropertiesFromAdapterSpecificAccountConfigWithPrecedenceForRequest() {
        // given
        final ObjectNode moduleConfig = mapper.createObjectNode();
        moduleConfig.put("enabled", true);
        moduleConfig.put("property1", "value1");
        moduleConfig.put("property2", "value2");

        final ObjectNode analyticsNode = mapper.createObjectNode();
        final ObjectNode logAnalyticsNode = mapper.createObjectNode();
        logAnalyticsNode.put("property1", "requestValue1");
        logAnalyticsNode.put("property3", "requestValue3");
        analyticsNode.set("logAnalytics", logAnalyticsNode);

        final AuctionContext auctionContext = AuctionContext.builder()
                .account(Account.builder()
                        .analytics(AccountAnalyticsConfig.of(
                                true, null, Map.of("logAnalytics", moduleConfig)))
                        .build())
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().analytics(analyticsNode).build()))
                        .build())
                .build();

        // when
        target.processEvent(AuctionEvent.builder().auctionContext(auctionContext).build());

        // then
        verify(vertx, times(2)).runOnContext(any());

        final ObjectNode expectedAnalyticsNode = mapper.createObjectNode();
        final ObjectNode expectedLogAnalyticsNode = mapper.createObjectNode();
        expectedLogAnalyticsNode.put("property1", "requestValue1");
        expectedLogAnalyticsNode.put("property2", "value2");
        expectedLogAnalyticsNode.put("property3", "requestValue3");
        expectedAnalyticsNode.set("logAnalytics", expectedLogAnalyticsNode);

        final ArgumentCaptor<AuctionEvent> auctionEventCaptor = ArgumentCaptor.forClass(AuctionEvent.class);
        verify(firstReporter).processEvent(auctionEventCaptor.capture());
        assertThat(auctionEventCaptor.getValue())
                .extracting(AuctionEvent::getAuctionContext)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .isEqualTo(expectedAnalyticsNode);
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
                        .account(Account.builder().build())
                        .bidRequest(bidRequestCustomizer.apply(BidRequest.builder()).build())
                        .build())
                .build();
    }
}
