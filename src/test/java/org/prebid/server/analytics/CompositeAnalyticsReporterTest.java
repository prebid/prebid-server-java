package org.prebid.server.analytics;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.*;

public class CompositeAnalyticsReporterTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;

    @Test
    public void shouldPassEventToAllDelegates() {
        // given
        final String event = StringUtils.EMPTY;

        final AnalyticsReporter reporter1 = mock(AnalyticsReporter.class);
        final AnalyticsReporter reporter2 = mock(AnalyticsReporter.class);
        final CompositeAnalyticsReporter analyticsReporter =
                new CompositeAnalyticsReporter(asList(reporter1, reporter2), vertx);

        willAnswer(withNullAndInvokeHandler()).given(vertx).runOnContext(any());

        // when
        analyticsReporter.processEvent(event);

        // then
        verify(vertx, times(2)).runOnContext(any());
        assertThat(captureEvent(reporter1)).isSameAs(event);
        assertThat(captureEvent(reporter2)).isSameAs(event);
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