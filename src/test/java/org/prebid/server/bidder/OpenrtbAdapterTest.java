package org.prebid.server.bidder;

import com.iab.openrtb.request.Imp;
import org.junit.Test;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.Video;
import org.prebid.server.proto.response.MediaType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenrtbAdapterTest {

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
        assertThatThrownBy(() -> OpenrtbAdapter.validateAdUnitBidsMediaTypes(adUnitBids,
                Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video))))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Invalid AdUnit: VIDEO media type with no video data");
    }

    @Test
    public void validateAdUnitBidsMediaTypesShouldNotThrowExceptionWhenVideoTypeIsNotValidAndNotAllowedMediaType() {
        // given
        final List<AdUnitBid> adUnitBids = singletonList(AdUnitBid.builder()
                .mediaTypes(singleton(MediaType.video))
                .video(Video.builder()
                        .mimes(emptyList())
                        .build())
                .build());

        // when and then
        assertThatCode(() -> OpenrtbAdapter.validateAdUnitBidsMediaTypes(adUnitBids, singleton(MediaType.banner)))
                .doesNotThrowAnyException();
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
