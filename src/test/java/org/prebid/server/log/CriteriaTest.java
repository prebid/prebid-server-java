package org.prebid.server.log;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class CriteriaTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private Logger logger;

    @Test
    public void logResponseShouldLogResponseWhenAllNotNullCriteriasPresent() {
        // given
        final Criteria criteria = Criteria.create("account", null, Logger::error);

        // when
        criteria.logResponse("Response has account", logger);

        // then
        verify(logger).error(anyString());
    }

    @Test
    public void logResponseShouldNotLogResponseWhenOneOfNotNullCriteriaMissing() {
        // given
        final Criteria criteria = Criteria.create("account", "bidder", Logger::error);

        // when
        criteria.logResponse("Response has account", logger);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void logResponseAndRequestShouldLogResponseWhenAllNotNullCriteriasPresent() {
        // given
        final Criteria criteria = Criteria.create("account", null, Logger::error);

        // when
        criteria.logResponseAndRequest("Response has account", "Request", logger);

        // then
        verify(logger, times(2)).error(anyString());
    }

    @Test
    public void logResponseAndRequestShouldNotLogResponseWhenOneOfNotNullCriteriaMissing() {
        // given
        final Criteria criteria = Criteria.create("account", "bidder", Logger::error);

        // when
        criteria.logResponseAndRequest("Response has account", "Request", logger);

        // then
        verifyNoInteractions(logger);
    }
}
