package org.prebid.server.deals.targeting.interpret;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.deals.targeting.RequestContext;
import org.prebid.server.deals.targeting.model.GeoLocation;
import org.prebid.server.deals.targeting.model.GeoRegion;
import org.prebid.server.deals.targeting.syntax.TargetingCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;

public class WithinTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RequestContext context;

    private Expression expression;
    private TargetingCategory category;

    @Before
    public void setUp() {
        // given
        category = new TargetingCategory(TargetingCategory.Type.location);
        expression = new Within(category, GeoRegion.of(50.424782f, 30.506423f, 10f));
    }

    @Test
    public void matchesShouldReturnTrueWhenThereIsMatch() {
        // given
        willReturn(GeoLocation.of(50.442406f, 30.521439f)).given(context).lookupGeoLocation(any());

        // when and then
        assertThat(expression.matches(context)).isTrue();
        verify(context).lookupGeoLocation(eq(category));
    }

    @Test
    public void matchesShouldReturnFalseWhenThereIsNoMatch() {
        // given
        willReturn(GeoLocation.of(50.588196f, 30.512357f)).given(context).lookupGeoLocation(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }

    @Test
    public void matchesShouldReturnFalseWhenActualValueIsMissing() {
        // given
        willReturn(null).given(context).lookupGeoLocation(any());

        // when and then
        assertThat(expression.matches(context)).isFalse();
    }
}
