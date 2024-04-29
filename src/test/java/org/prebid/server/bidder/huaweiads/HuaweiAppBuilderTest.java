package org.prebid.server.bidder.huaweiads;

import com.iab.openrtb.request.Content;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.bidder.huaweiads.model.request.App;
import org.prebid.server.bidder.huaweiads.model.request.PkgNameConvert;
import org.prebid.server.exception.PreBidException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HuaweiAppBuilderTest {

    private HuaweiAppBuilder target;

    @Before
    public void before() {
        target = new HuaweiAppBuilder(Collections.emptyList());
    }

    @Test
    public void buildShouldReturnNullWhenRequestAppIsNull() {
        // given & when
        final App actual = target.build(null, "UA");

        // then
        assertThat(actual).isNull();
    }

    @Test
    public void buildShouldReturnFullAppWhenRequestAppHasAllFieldsDefined() {
        // given
        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .ver("version")
                .name("name")
                .bundle("bundle")
                .content(Content.builder().language("UA").build())
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("UA")
                .pkgName("bundle")
                .name("name")
                .version("version")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppWithLangPkgNameCountryCodeWhenRequestAppHasOnlyBundleAndPkgConvertersNotProvided() {
        // given
        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("bundle")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnErrorWhenRequestAppBundleIsAbsent() {
        // given
        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .ver("version")
                .name("name")
                .bundle("")
                .content(Content.builder().language("UA").build())
                .build();

        // when & then
        assertThatThrownBy(() -> target.build(app, "DE"))
                .isInstanceOf(PreBidException.class)
                .hasMessage("generate HuaweiAds AppInfo failed: openrtb BidRequest.App.Bundle is empty.");
    }

    @Test
    public void buildShouldReturnAppWithBundleNameAsConvertedPkgNameWhenExceptionPkgNamesContainsBundleName() {
        // given
        final List<String> exceptionPkgNames = List.of("bundle", "some pkg name");
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(exceptionPkgNames)
                .unconvertedPkgNames(Collections.emptyList())
                .unconvertedPkgNameKeyWords(Collections.emptyList())
                .unconvertedPkgNamePrefixs(Collections.emptyList())
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("bundle")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppWithConvertedPkgNameWhenUnconvertedPkgNamesContainsBundleName() {
        // given
        final List<String> unconvertedPkgNames = List.of("bundle", "some pkg name");
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(Collections.emptyList())
                .unconvertedPkgNames(unconvertedPkgNames)
                .unconvertedPkgNameKeyWords(Collections.emptyList())
                .unconvertedPkgNamePrefixs(Collections.emptyList())
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("converted pkg name")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppWithConvertedPkgNameWhenUnconvertedPkgNamesContainsAsterix() {
        // given
        final List<String> unconvertedPkgNames = List.of("*", "some pkg name");
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(Collections.emptyList())
                .unconvertedPkgNames(unconvertedPkgNames)
                .unconvertedPkgNameKeyWords(Collections.emptyList())
                .unconvertedPkgNamePrefixs(Collections.emptyList())
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("converted pkg name")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppWithConvertedPkgNameWhenUnconvertedPkgNameKeywordsAreUsedInBundleName() {
        // given
        final List<String> unconvertedPkgNameKeyWords = List.of("some other keyword", "undle");
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(Collections.emptyList())
                .unconvertedPkgNames(Collections.emptyList())
                .unconvertedPkgNameKeyWords(unconvertedPkgNameKeyWords)
                .unconvertedPkgNamePrefixs(Collections.emptyList())
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("converted pkg name")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppWithConvertedPkgNameWhenBundleNameStartsWithOneOfUnconvertedPkgNamePrefix() {
        // given
        final List<String> unconvertedPkgNamePrefixs = List.of("some other keyword", "bun");
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(Collections.emptyList())
                .unconvertedPkgNames(Collections.emptyList())
                .unconvertedPkgNameKeyWords(Collections.emptyList())
                .unconvertedPkgNamePrefixs(unconvertedPkgNamePrefixs)
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("converted pkg name")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void buildShouldReturnAppBundleNameAsConvertedPkgNameWhenPkgConvertersHasOnlyConvertedPkgNames() {
        // given
        target = new HuaweiAppBuilder(List.of(PkgNameConvert.builder()
                .convertedPkgName("converted pkg name")
                .exceptionPkgNames(Collections.emptyList())
                .unconvertedPkgNames(Collections.emptyList())
                .unconvertedPkgNameKeyWords(Collections.emptyList())
                .unconvertedPkgNamePrefixs(Collections.emptyList())
                .build()));

        final com.iab.openrtb.request.App app = com.iab.openrtb.request.App.builder()
                .bundle("bundle")
                .build();

        // when
        final App actual = target.build(app, "DE");

        // then
        final App expected = App.builder()
                .lang("en")
                .pkgName("bundle")
                .country("DE")
                .build();

        assertThat(actual).isEqualTo(expected);
    }

}
