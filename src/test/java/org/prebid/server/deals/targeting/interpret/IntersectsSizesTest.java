package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.Size;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

public class IntersectsSizesTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = TargetingCategory.fromString("adunit.size");
        expression = new IntersectsSizes(category, asList(Size.of(250, 300), Size.of(300, 350), Size.of(350, 400)));
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatch() {
        // given
        willReturn(asList(Size.of(300, 350), Size.of(350, 400), Size.of(450, 500))).given(context).lookupSizes(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupSizes(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatch() {
        // given
        willReturn(asList(Size.of(450, 500), Size.of(500, 550))).given(context).lookupSizes(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        willReturn(emptyList()).given(context).lookupSizes(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsNotDefined() {
        // given
        willReturn(null).given(context).lookupSizes(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
