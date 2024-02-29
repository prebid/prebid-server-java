package org.prebid.server.util;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.bidder.model.PriceFloorInfo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

public class BidderUtilTest {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void isValidPriceShouldReturnFalseIfPriceIsMissing() {
        // when and then
        assertThat(BidderUtil.isValidPrice((BigDecimal) null)).isFalse();
    }

    @Test
    public void isValidPriceShouldReturnFalseIfPriceIsLessThenZero() {
        // when and then
        assertThat(BidderUtil.isValidPrice(BigDecimal.valueOf(-1))).isFalse();
    }

    @Test
    public void isValidPriceShouldReturnFalseIfPriceIsZero() {
        // when and then
        assertThat(BidderUtil.isValidPrice(BigDecimal.ZERO)).isFalse();
    }

    @Test
    public void isValidPriceShouldReturnTrueIfPriceIsGreaterThenZero() {
        // when and then
        assertThat(BidderUtil.isValidPrice(BigDecimal.ONE)).isTrue();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfBidIsMissing() {
        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(null, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfBidImpIdIsMissing() {
        // given
        final Bid bid = givenBid(builder -> builder.impid(null));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfRequestImpsAreMissing() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(null));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfRequestImpsAreEmpty() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(builder -> builder.imp(emptyList()));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfNoCorrespondingImpFound() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.id("other"))));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnNullIfImpFloorAndCurrencyAreMissing() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(null).bidfloorcur(null))));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void resolvePriceFloorShouldReturnPriceFloorInfoIfImpFloorIsDefined() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloor(BigDecimal.ZERO))));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isEqualTo(PriceFloorInfo.of(BigDecimal.ZERO, null));
    }

    @Test
    public void resolvePriceFloorShouldReturnPriceFloorInfoIfImpFloorCurrencyIsDefined() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.imp(givenImps(impBuilder -> impBuilder.bidfloorcur("JPY"))));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isEqualTo(PriceFloorInfo.of(null, "JPY"));
    }

    @Test
    public void resolvePriceFloorShouldReturnPriceFloorInfoIfBothImpFloorAndCurrencyAreDefined() {
        // given
        final Bid bid = givenBid(identity());
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.imp(givenImps(
                        impBuilder -> impBuilder.bidfloor(BigDecimal.ONE).bidfloorcur("USD"))));

        // when
        final PriceFloorInfo result = BidderUtil.resolvePriceFloor(bid, bidRequest);

        // then
        assertThat(result).isEqualTo(PriceFloorInfo.of(BigDecimal.ONE, "USD"));
    }

    @Test
    public void getBidTypeShouldReturnBannerBidTypeWhenBannerImpIsPresent() {
        // given
        final Bid bannerBid = givenBid(bidBuilder -> bidBuilder.impid("imp_banner"));
        final Imp bannerImp = givenImp(impBuilder -> impBuilder.id("imp_banner").banner(Banner.builder().build()));
        final Map<String, Imp> impMap = Map.of("imp_banner", bannerImp);

        // when
        final BidType actual = BidderUtil.getBidType(bannerBid, impMap);

        // then
        assertThat(actual).isEqualTo(BidType.banner);
    }

    @Test
    public void getBidTypeShouldReturnBannerBidTypeWhenVideoImpIsPresent() {
        // given
        final Bid videoBid = givenBid(bidBuilder -> bidBuilder.impid("imp_video"));
        final Imp videoImp = givenImp(impBuilder -> impBuilder.id("imp_video").video(Video.builder().build()));
        final Map<String, Imp> impMap = Map.of("imp_video", videoImp);

        // when
        final BidType actual = BidderUtil.getBidType(videoBid, impMap);

        // then
        assertThat(actual).isEqualTo(BidType.video);
    }

    @Test
    public void getBidTypeShouldReturnBannerBidTypeWhenAudioImpIsPresent() {
        // given
        final Bid audioBid = givenBid(bidBuilder -> bidBuilder.impid("imp_audio"));
        final Imp audioImp = givenImp(impBuilder -> impBuilder.id("imp_audio").audio(Audio.builder().build()));
        final Map<String, Imp> impMap = Map.of("imp_audio", audioImp);

        // when
        final BidType actual = BidderUtil.getBidType(audioBid, impMap);

        // then
        assertThat(actual).isEqualTo(BidType.audio);
    }

    @Test
    public void getBidTypeShouldReturnBannerBidTypeWhenNativeImpIsPresent() {
        // given
        final Bid nativeBid = givenBid(bidBuilder -> bidBuilder.impid("imp_native"));
        final Imp nativeImp = givenImp(impBuilder -> impBuilder.id("imp_native").xNative(Native.builder().build()));
        final Map<String, Imp> impMap = Map.of("imp_native", nativeImp);

        // when
        final BidType actual = BidderUtil.getBidType(nativeBid, impMap);

        // then
        assertThat(actual).isEqualTo(BidType.xNative);
    }

    @Test
    public void getBidTypeShouldReturnBannerBidTypeWhenImpForBidIsAbsent() {
        // given
        final Bid unknownBid = givenBid(bidBuilder -> bidBuilder.impid("imp_unknown"));

        // when
        final BidType actual = BidderUtil.getBidType(unknownBid, Map.of());

        // then
        assertThat(actual).isEqualTo(BidType.banner);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(givenImps(identity())))
                .build();
    }

    private static List<Imp> givenImps(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return singletonList(givenImp(impCustomizer));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("impId")).build();
    }

    private static Bid givenBid(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder().impid("impId")).build();
    }
}
