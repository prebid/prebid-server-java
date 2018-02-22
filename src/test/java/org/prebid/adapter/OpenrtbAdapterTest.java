package org.prebid.adapter;

import com.iab.openrtb.request.Imp;
import org.junit.Test;
import org.prebid.exception.PreBidException;
import org.prebid.model.AdUnitBid;
import org.prebid.model.MediaType;
import org.prebid.model.request.Video;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.*;

public class OpenrtbAdapterTest {

    @Test
    public void validateUrlShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> OpenrtbAdapter.validateUrl(null));
    }

    @Test
    public void validateUrlShouldFailOnInvalidUrl() {
        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OpenrtbAdapter.validateUrl("invalid_url"))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void validateUrlShouldReturnExpectedUrl() {
        // when
        final String url = OpenrtbAdapter.validateUrl("http://domain.org/query-string?a=1");

        // then
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("http://domain.org/query-string?a=1");
    }

    @Test
    public void encodeUrlShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> OpenrtbAdapter.encodeUrl(null));
    }

    @Test
    public void encodeUrlShouldReturnExpectedValue() {
        // when
        final String url = OpenrtbAdapter.encodeUrl("//domain.org/%s", "query-string?a=1");

        // then
        assertThat(url).isNotNull();
        assertThat(url).isEqualTo("%2F%2Fdomain.org%2Fquery-string%3Fa%3D1");
    }

    @Test
    public void validateAdUnitBidsMediaTypesShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> OpenrtbAdapter.validateAdUnitBidsMediaTypes(null));
    }

    @Test
    public void validateAdUnitBidsMediaTypesShouldFailWhenMediaTypeIsVideoAndMimesListIsEmpty() {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(AdUnitBid.builder()
                .mediaTypes(singleton(MediaType.video))
                .video(Video.builder()
                        .mimes(emptyList())
                        .build())
                .build());

        // when and then
        assertThatThrownBy(() -> OpenrtbAdapter.validateAdUnitBidsMediaTypes(adUnitBids))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void allowedMediaTypesShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> OpenrtbAdapter.allowedMediaTypes(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> OpenrtbAdapter.allowedMediaTypes(AdUnitBid.builder().build(), null));
    }

    @Test
    public void allowedMediaTypesShouldReturnExpectedMediaTypes() {
        // given
        final AdUnitBid adUnitBid = AdUnitBid.builder()
                .mediaTypes(singleton(MediaType.video))
                .build();
        final Set<MediaType> mediaTypes = Stream.of(MediaType.banner, MediaType.video)
                .collect(Collectors.toSet());

        // when
        final Set<MediaType> allowedMediaTypes = OpenrtbAdapter.allowedMediaTypes(adUnitBid, mediaTypes);

        // then
        assertThat(allowedMediaTypes).containsOnly(MediaType.video);
    }

    @Test
    public void validateImpsShouldFailOnNullOrEmptyArgument() {
        assertThatThrownBy(() -> OpenrtbAdapter.validateImps(null))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");

        assertThatThrownBy(() -> OpenrtbAdapter.validateImps(emptyList()))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("openRTB bids need at least one Imp");
    }

    @Test
    public void validateImpsShouldAllowListOfImps() {
        assertThatCode(() -> OpenrtbAdapter.validateImps(singletonList(Imp.builder().build())))
                .doesNotThrowAnyException();
    }

    @Test
    public void lookupBidShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> OpenrtbAdapter.lookupBid(null, null));
    }

    @Test
    public void lookupBidShouldFailWhenBidNotFound() {
        assertThatThrownBy(() -> OpenrtbAdapter.lookupBid(emptyList(), null))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Unknown ad unit code 'null'");
    }

    @Test
    public void lookupBidShouldReturnExpectedValue() {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(AdUnitBid.builder().adUnitCode("adUnitCode1").build());

        // when
        final AdUnitBid adUnitBid = OpenrtbAdapter.lookupBid(adUnitBids, "adUnitCode1");

        // then
        assertThat(adUnitBid).isNotNull()
                .isEqualTo(AdUnitBid.builder().adUnitCode("adUnitCode1").build());
    }
}
