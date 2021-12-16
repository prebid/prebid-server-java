package org.prebid.server.log;

import io.vertx.core.logging.Logger;
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
    public void logShouldChooseCriteriaLoggerLevelWhenAccountsMatchedAndElseFieldsAreNull() {
        // given
        final Criteria criteria = Criteria.create("account", null, null, Logger::error);

        // when
        criteria.log(Criteria.builder().account("account").bidder("rubicon").build(), logger, "message", logger::info);

        // then
        verify(logger).error(anyString());
    }

    @Test
    public void logShouldChooseDefaultLogLevelWhenAccountsMatchedAndElseFieldsAreNotNullAndNotMatched() {
        // given
        final Criteria criteria = Criteria.create("account", "appnexus", null, Logger::error);

        // when
        criteria.log(Criteria.builder().account("account").bidder("rubicon").build(),
                logger, "message", logger::info);

        // then
        verify(logger).info(anyString());
    }

    @Test
    public void logShouldChooseCriteriaLogLevelAccountAndBidderMatchedLineItemIsNull() {
        // given
        final Criteria criteria = Criteria.create("account", "rubicon", null, Logger::error);

        // when
        criteria.log(Criteria.builder().account("account").bidder("rubicon")
                .lineItemId("lineItemId").build(), logger, "message", logger::info);

        // then
        verify(logger).error(anyString());
    }

    @Test
    public void logShouldChooseCriteriaLogLevelWhenAllMatched() {
        // given
        final Criteria criteria = Criteria.create("account", "rubicon", "lineItemId", Logger::error);

        // when
        criteria.log(Criteria.builder().account("account").bidder("rubicon")
                .lineItemId("lineItemId").build(), logger, "message", logger::info);

        // then
        verify(logger).error(anyString());
    }

    @Test
    public void logResponseShouldLogResponseWhenAllNotNullCriteriasPresent() {
        // given
        final Criteria criteria = Criteria.create("account", null, "lineItemId", Logger::error);

        // when
        criteria.logResponse("Response has account and lineItemId", logger);

        // then
        verify(logger).error(anyString());
    }

    @Test
    public void logResponseShouldNotLogResponseWhenOneOfNutNullCriteriaMissing() {
        // given
        final Criteria criteria = Criteria.create("account", null, "lineItemId", Logger::error);

        // when
        criteria.logResponse("Response has account", logger);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void logResponseAndRequestShouldLogResponseWhenAllNotNullCriteriasPresent() {
        // given
        final Criteria criteria = Criteria.create("account", null, "lineItemId", Logger::error);

        // when
        criteria.logResponseAndRequest("Response has account", "Request has lineItemId", logger);

        // then
        verify(logger, times(2)).error(anyString());
    }

    @Test
    public void logResponseAndRequestShouldNotLogResponseWhenOneOfNutNullCriteriaMissing() {
        // given
        final Criteria criteria = Criteria.create("account", null, "lineItemId", Logger::error);

        // when
        criteria.logResponseAndRequest("Response has account", "Request", logger);

        // then
        verifyNoInteractions(logger);
    }
}
