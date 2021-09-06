package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

public class IntersectsIntegersTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = TargetingCategory.fromString("bidp.rubicon.invCodes");
        expression = new IntersectsIntegers(category, asList(1, 2, 3));
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatch() {
        // given
        willReturn(asList(2, 3, 4)).given(context).lookupIntegers(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupIntegers(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatch() {
        // given
        willReturn(asList(4, 5)).given(context).lookupIntegers(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        willReturn(emptyList()).given(context).lookupIntegers(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
