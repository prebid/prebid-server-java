package org.prebid.server.auction;

import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidInfo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class WinningBidComparatorFactoryTest {

    private final WinningBidComparatorFactory winningBidComparatorFactory = new WinningBidComparatorFactory();

    @Test
    public void preferDealsComparatorCompareShouldReturnMoreThanZeroForNonDealsWhenFirstHasHigherPrice() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(higherPriceBidInfo, lowerPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnLessThanZeroForNonDealsWhenFirstHasLowerPrice() {
        // given
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f);
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(lowerPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnZeroWhenPriceAreEqualForNonDealsBids() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f);
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnGreaterThanZeroWhenBidWithLowerPriceHasDeal() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnLowerThanZeroWhenBothBidsHasDealsAndFirstHasLessPrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnZeroForDealsBidsWithSamePrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(4.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void sortShouldReturnExpectedSortedResultForPreferDealsComparator() {
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
        bidInfos.sort(winningBidComparatorFactory.create(true));

        // then
        assertThat(bidInfos).containsOnly(bidInfo2, bidInfo1, bidInfo8, bidInfo7, bidInfo6, bidInfo5, bidInfo4,
                bidInfo3);
    }

    @Test
    public void priceComparatorCompareShouldReturnMoreThatZeroWhenFirstHasHigherPriceForNonDealsBids() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(higherPriceBidInfo, lowerPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnLessThatZeroWhenFirstHasLowerPriceForNonDealsBids() {
        // given
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f);
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(lowerPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnZeroWhenPriceAreEqualForNonDealsBids() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f);
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnLowerThanZeroWhenFirstBidWithLowerPriceHasDeal() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnLowerThanZeroWhenBothBidsHasDealsAndFirstHasLessPrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnZeroForDealsBidsWithSamePrice() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(4.0f, "dealId");
        final BidInfo bidInfo2 = givenBidInfo(4.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void priceComparatorSortShouldReturnExpectedSortedResult() {
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
        bidInfos.sort(winningBidComparatorFactory.create(false));

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
