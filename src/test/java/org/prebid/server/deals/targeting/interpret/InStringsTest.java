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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

public class InStringsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = new TargetingCategory(TargetingCategory.Type.referrer);
        expression = new InStrings(category, asList("Munich", "Berlin", "Stuttgart"));
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatch() {
        // given
        willReturn("Berlin").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupString(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatch() {
        // given
        willReturn("Ingolstadt").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldPerformCaseInsensitiveComparison() {
        // given
        willReturn("BERLIN").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        willReturn(null).given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
