package org.prebid.server.auction;

import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidInfo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class WinningBidComparatorTest {

    private final WinningBidComparator target = new WinningBidComparator();

    @Test
    public void compareShouldReturnMoreThatZeroWhenFirstHasHigherPrice() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);
        final BidInfo loverPriceBidInfo = givenBidInfo(1.0f);

        // when
        final int result = target.compare(higherPriceBidInfo, loverPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void compareShouldReturnLessThatZeroWhenFirstHasLowerPrice() {
        // given
        final BidInfo loverPriceBidInfo = givenBidInfo(1.0f);
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);

        // when
        final int result = target.compare(loverPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void compareShouldReturnZeroWhenPriceAreEqual() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f);
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = target.compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void compareShouldReturnGreaterThanZeroWhenBidWithLowerPriceHasDeal() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = target.compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void compareShouldReturnLowerThanZeroWhenBothBidsHasDealsAndFirstHasLessPrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId2");

        // when
        final int result = target.compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(-1);
    }

    @Test
    public void compareShouldReturnZeroForDealsBidsWithSamePrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(4.0f, "dealId2");

        // when
        final int result = target.compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void sortShouldReturnExpectedSortedResult() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(1.0f, "dealId1");
        final BidInfo bidInfo2 = givenBidInfo(2.0f, "dealId2");
        final BidInfo bidInfo3 = givenBidInfo(1.0f);
        final BidInfo bidInfo4 = givenBidInfo(2.0f);
        final BidInfo bidInfo5 = givenBidInfo(4.1f);
        final BidInfo bidInfo6 = givenBidInfo(4.4f);
        final BidInfo bidInfo7 = givenBidInfo(5.0f);
        final BidInfo bidInfo8 = givenBidInfo(100.1f);

        final List<BidInfo> bidInfos = Arrays.asList(bidInfo5, bidInfo3, bidInfo1, bidInfo2, bidInfo1, bidInfo7,
                bidInfo8, bidInfo4, bidInfo6);

        // when
        bidInfos.sort(target);

        // then
        assertThat(bidInfos).containsOnly(bidInfo2, bidInfo1, bidInfo8, bidInfo7, bidInfo6, bidInfo5, bidInfo4,
                bidInfo3);
    }

    private static BidInfo givenBidInfo(float price) {
        return givenBidInfo(price, null);
    }

    private static BidInfo givenBidInfo(float price, String dealId) {
        return BidInfo.builder()
                .bid(Bid.builder().price(BigDecimal.valueOf(price)).dealid(dealId).build())
                .build();
    }
}
