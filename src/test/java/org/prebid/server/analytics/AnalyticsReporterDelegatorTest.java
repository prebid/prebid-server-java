package org.prebid.server.analytics;

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
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;

import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
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

    private AnalyticsReporter firstReporter;

    private AnalyticsReporter secondReporter;

    private AnalyticsReporterDelegator target;

    @Before
    public void setUp() {
        firstReporter = mock(AnalyticsReporter.class);
        given(firstReporter.vendorId()).willReturn(FIRST_REPORTER_ID);

        secondReporter = mock(AnalyticsReporter.class);
        given(secondReporter.vendorId()).willReturn(SECOND_REPORTER_ID);

        target = new AnalyticsReporterDelegator(asList(firstReporter, secondReporter), vertx,
                privacyEnforcementService);
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
        verify(vertx, times(1)).runOnContext(any());
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
}
