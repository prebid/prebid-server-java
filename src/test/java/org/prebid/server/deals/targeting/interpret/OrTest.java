package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class OrTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TerminalExpression trueExpression;
    @Mock
    private TerminalExpression falseExpression;
    @Mock
    private RequestContext context;

    @Before
    public void setUp() {
        given(trueExpression.matches(any())).willReturn(true);
        given(falseExpression.matches(any())).willReturn(false);
    }

    @Test
    public void matchesShouldReturnTrue() {
        assertThat(new Or(asList(trueExpression, trueExpression, trueExpression)).matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalse() {
        assertThat(new Or(asList(falseExpression, falseExpression, falseExpression)).matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnTrueAndNotEvaluateRemainingExpressions() {
        assertThat(new Or(asList(falseExpression, trueExpression, falseExpression)).matches(context)).isTrue();

        verify(falseExpression).matches(context);
        verify(trueExpression).matches(context);
    }
}
