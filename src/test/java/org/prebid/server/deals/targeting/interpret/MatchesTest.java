package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

public class MatchesTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = TargetingCategory.fromString("adunit.adslot");
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForEquals() {
        // given
        expression = new Matches(category, "adunit");

        willReturn("adunit").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupString(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatchForEquals() {
        // given
        expression = new Matches(category, "adunit");

        willReturn("notadunit").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForStartsWith() {
        // given
        expression = new Matches(category, "adunit*");

        willReturn("adunitOne").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatchForStartsWith() {
        // given
        expression = new Matches(category, "adunit");

        willReturn("somedunit").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForEndsWith() {
        // given
        expression = new Matches(category, "*adunit");

        willReturn("someadunit").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatchForEndsWith() {
        // given
        expression = new Matches(category, "*adunit");

        willReturn("adunitOne").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForContainsInTheMiddle() {
        // given
        expression = new Matches(category, "*adunit*");

        willReturn("someadunitOne").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForContainsInTheBeginning() {
        // given
        expression = new Matches(category, "*adunit*");

        willReturn("adunitOne").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatchForContainsInTheEnd() {
        // given
        expression = new Matches(category, "*adunit*");

        willReturn("someadunit").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatchForContains() {
        // given
        expression = new Matches(category, "*adunit*");

        willReturn("One").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldPerformCaseInsensitiveComparison() {
        // given
        expression = new Matches(category, "AdUnIt");

        willReturn("aDuNiT").given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        expression = new Matches(category, "adunit");

        willReturn(null).given(context).lookupString(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
