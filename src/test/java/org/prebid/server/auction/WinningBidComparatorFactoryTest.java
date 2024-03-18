package org.prebid.server.auction;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidInfo;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class WinningBidComparatorFactoryTest {

    private static final String IMP_ID = "impId";
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
    public void preferDealsComparatorCompareShouldReturnMoreThanZeroWhenFirstHasDeal() {
        // given
        final BidInfo dealPriceBidInfo = givenBidInfo(1.0f, "dealId");
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(dealPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnLessThanZeroWhenSecondHasDeal() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);
        final BidInfo dealPriceBidInfo = givenBidInfo(1.0f, "dealId");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(higherPriceBidInfo, dealPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnMoreThanZeroWhenBothHaveDealsAndFirstHasHigherPrice() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f, "dealId1");
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(higherPriceBidInfo, lowerPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnLessThanZeroWhenBothHaveDealsAndFirstHasLowerPrice() {
        // given
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f, "dealId1");
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(lowerPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnZeroWhenBothHaveDealsAndPriceAreEqual() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId1");
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId2");

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferDealsComparatorSortShouldReturnExpectedSortedResultWithDeals() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f); // non deal with lower price
        final BidInfo bidInfo2 = givenBidInfo(100.1f); // non deal with higher price
        final BidInfo bidInfo3 = givenBidInfo(0.5f, "dealId1"); // deal with lower price
        final BidInfo bidInfo4 = givenBidInfo(1f, "dealId2"); // deal with middle price
        final BidInfo bidInfo5 = givenBidInfo(6f, "dealId3"); // deal with higher price

        final List<BidInfo> bidInfos = asList(bidInfo5, bidInfo4, bidInfo2, bidInfo3, bidInfo1, bidInfo2);

        // when
        bidInfos.sort(winningBidComparatorFactory.create(true));

        // then
        assertThat(bidInfos).containsExactly(bidInfo1, bidInfo2, bidInfo2, bidInfo3, bidInfo4, bidInfo5);
    }

    @Test
    public void priceComparatorCompareShouldReturnMoreThatZeroWhenFirstHasHigherPrice() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(5.0f);
        final BidInfo lowerPriceBidInfo = givenBidInfo(1.0f);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(higherPriceBidInfo, lowerPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void priceComparatorCompareShouldReturnLessThatZeroWhenFirstHasLowerPrice() {
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
    public void preferPriceComparatorSortShouldReturnExpectedSortedResultWithDeals() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f, null); // non deal with lower price
        final BidInfo bidInfo2 = givenBidInfo(100.1f, null); // non deal with higher price
        final BidInfo bidInfo3 = givenBidInfo(0.5f, "dealId1"); // deal with lower price
        final BidInfo bidInfo4 = givenBidInfo(1f, "dealId2"); // deal with middle price
        final BidInfo bidInfo5 = givenBidInfo(6f, "dealId3"); // deal with higher price

        final List<BidInfo> bidInfos = asList(bidInfo5, bidInfo4, bidInfo2, bidInfo3, bidInfo1, bidInfo2);

        // when
        bidInfos.sort(winningBidComparatorFactory.create(false));

        // then
        assertThat(bidInfos).containsExactly(bidInfo3, bidInfo4, bidInfo1, bidInfo5, bidInfo2, bidInfo2);
    }

    private static BidInfo givenBidInfo(float price) {
        return givenBidInfo(price, null);
    }

    private static BidInfo givenBidInfo(float price, String dealId) {
        return BidInfo.builder()
                .correspondingImp(Imp.builder().build())
                .bid(Bid.builder().impid(IMP_ID).price(BigDecimal.valueOf(price)).dealid(dealId).build())
                .correspondingImp(Imp.builder().id(IMP_ID).build())
                .build();
    }
}
