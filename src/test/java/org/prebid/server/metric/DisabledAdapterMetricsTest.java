package org.prebid.server.metric;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

public class DisabledAdapterMetricsTest {

    @Mock
    private DisabledAdapterMetrics disabledAdapterMetrics;

    @Before
    public void setUp() {
        disabledAdapterMetrics = mock(DisabledAdapterMetrics.class, CALLS_REAL_METHODS);
    }

    @Test
    public void incCounterShouldDoNothing() {
        doNothing().when(disabledAdapterMetrics).incCounter(isA(MetricName.class));
    }

    @Test
    public void incCounterWithValueShouldDoNothing() {
        doNothing().when(disabledAdapterMetrics).incCounter(isA(MetricName.class), isA(Long.class));
    }

    @Test
    public void updateTimerShouldDoNothing() {
        doNothing().when(disabledAdapterMetrics).updateTimer(isA(MetricName.class), isA(Long.class));
    }

    @Test
    public void updateHistogramShouldDoNothing() {
        doNothing().when(disabledAdapterMetrics).updateHistogram(isA(MetricName.class), isA(Long.class));
    }
}
