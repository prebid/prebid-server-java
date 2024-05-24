package org.prebid.server.log;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class CriteriaManagerTest extends VertxTest {

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private CriteriaLogManager criteriaLogManager;
    @Mock
    private Vertx vertx;

    private CriteriaManager criteriaManager;

    @Before
    public void setUp() {
        criteriaManager = new CriteriaManager(criteriaLogManager, vertx);
    }

    @Test
    public void addCriteriaShouldThrowIllegalArgumentExceptionWhenLoggerLevelHasInvalidValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> criteriaManager.addCriteria("1001", "rubicon", "invalid", 800))
                .withMessage("Invalid LoggingLevel: invalid");
    }

    @Test
    public void addCriteriaShouldAddVertxTimerWithLimitedDurationInMillis() {
        // given and when
        criteriaManager.addCriteria("1001", "rubicon", "error", 800000);

        // then
        verify(vertx).setTimer(eq(300000L), any());
    }

    @Test
    public void addCriteriaShouldAddVertxTimerWithDefaultDurationInMillis() {
        // given and when
        criteriaManager.addCriteria("1001", "rubicon", "error", 200000);

        // then
        verify(vertx).setTimer(eq(200000L), any());
    }
}
