package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

public class NotTest {

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
        assertThat(new Not(trueExpression).matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalse() {
        assertThat(new Not(falseExpression).matches(context)).isTrue();
    }
}
