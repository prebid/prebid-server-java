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

public class IntersectsStringsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = TargetingCategory.fromString("adunit.mediatype");
        expression = new IntersectsStrings(category, asList("Pop", "Rock", "Alternative"));
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatch() {
        // given
        willReturn(asList("Rock", "Alternative", "Folk")).given(context).lookupStrings(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupStrings(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatch() {
        // given
        willReturn(asList("Folk", "Trance")).given(context).lookupStrings(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldPerformCaseInsensitiveComparison() {
        // given
        willReturn(asList("ROCK", "ALTERNATIVE", "FOLK")).given(context).lookupStrings(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        willReturn(emptyList()).given(context).lookupStrings(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsNotDefined() {
        // given
        willReturn(null).given(context).lookupStrings(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
