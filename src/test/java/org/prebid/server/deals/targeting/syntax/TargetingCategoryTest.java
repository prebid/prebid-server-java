package org.prebid.server.deals.targeting.syntax;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;
import org.prebid.server.exception.TargetingSyntaxException;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TargetingCategoryTest {

    @Test
    public void isTargetingCategoryShouldReturnTrueForKnownCategories() {
        final List<String> categories = asList(
                "adunit.size",
                "adunit.mediatype",
                "adunit.adslot",
                "site.domain",
                "site.publisher.domain",
                "site.referrer",
                "app.bundle",
                "device.geo.ext.vendor.attribute",
                "device.geo.ext.vendor.nested.attribute",
                "device.ext.vendor.attribute",
                "device.ext.vendor.nested.attribute",
                "pos",
                "geo.distance",
                "segment.bluekai",
                "user.ext.time.userdow",
                "user.ext.time.userhour",
                "bidp.rubicon.siteId",
                "ufpd.sport",
                "sfpd.sport");

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            categories.forEach(categoryString ->
                    softly.assertThat(TargetingCategory.isTargetingCategory(categoryString)).isTrue());
        }
    }

    @Test
    public void isTargetingCategoryShouldReturnFalseForUnknownCategory() {
        assertThat(TargetingCategory.isTargetingCategory("phony")).isFalse();
    }

    @Test
    public void fromStringShouldReturnCategoryWithoutPathForStaticTypes() {
        final List<String> categories = asList(
                "adunit.size",
                "adunit.mediatype",
                "adunit.adslot",
                "site.domain",
                "site.publisher.domain",
                "site.referrer",
                "app.bundle",
                "pos",
                "geo.distance",
                "user.ext.time.userdow",
                "user.ext.time.userhour");

        try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            for (final String categoryString : categories) {
                final TargetingCategory category = TargetingCategory.fromString(categoryString);
                softly.assertThat(category.type()).isEqualTo(TargetingCategory.Type.fromString(categoryString));
                softly.assertThat(category.path()).isNull();
            }
        }
    }

    @Test
    public void fromStringShouldReturnCategoryWithPathForDynamicTypes() {
        // when
        final TargetingCategory bidderParamCategory = TargetingCategory.fromString("bidp.rubicon.siteId");
        // then
        assertThat(bidderParamCategory.type()).isEqualTo(TargetingCategory.Type.bidderParam);
        assertThat(bidderParamCategory.path()).isEqualTo("rubicon.siteId");

        // when
        final TargetingCategory userSegmentCategory = TargetingCategory.fromString("segment.bluekai");
        // then
        assertThat(userSegmentCategory.type()).isEqualTo(TargetingCategory.Type.userSegment);
        assertThat(userSegmentCategory.path()).isEqualTo("bluekai");

        // when
        final TargetingCategory userFirstPartyDataCategory = TargetingCategory.fromString("ufpd.sport");
        // then
        assertThat(userFirstPartyDataCategory.type()).isEqualTo(TargetingCategory.Type.userFirstPartyData);
        assertThat(userFirstPartyDataCategory.path()).isEqualTo("sport");

        // when
        final TargetingCategory siteFirstPartyDataCategory = TargetingCategory.fromString("sfpd.sport");
        // then
        assertThat(siteFirstPartyDataCategory.type()).isEqualTo(TargetingCategory.Type.siteFirstPartyData);
        assertThat(siteFirstPartyDataCategory.path()).isEqualTo("sport");
    }

    @Test
    public void fromStringShouldThrowExceptionWhenCategoryIsUnknown() {
        assertThatThrownBy(() -> TargetingCategory.fromString("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unrecognized targeting category: unknown");
    }

    @Test
    public void fromStringShouldThrowExceptionWhenBidderParamIsIncorrect() {
        assertThatThrownBy(() -> TargetingCategory.fromString("bidp.rubicon"))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("BidderParam path is incorrect: rubicon");

        assertThatThrownBy(() -> TargetingCategory.fromString("bidp.rubicon."))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("BidderParam path is incorrect: rubicon.");

        assertThatThrownBy(() -> TargetingCategory.fromString("bidp.rubicon.siteId."))
                .isInstanceOf(TargetingSyntaxException.class)
                .hasMessage("BidderParam path is incorrect: rubicon.siteId.");
    }
}
