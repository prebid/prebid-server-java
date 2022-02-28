package org.prebid.server.auction;

import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidInfo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
    public void preferDealsComparatorCompareShouldReturnMoreThanZeroWhenFirstHasNonPgDeal() {
        // given
        final BidInfo dealPriceBidInfo = givenBidInfo(5.0f, "dealId", emptyList());
        final BidInfo higherPriceBidInfo = givenBidInfo(10.0f, null, emptyList());

        // when
        final int result = winningBidComparatorFactory.create(true).compare(dealPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnLessThanZeroWhenFirstHasNoDeal() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(10.0f, null, emptyList());
        final BidInfo dealPriceBidInfo = givenBidInfo(5.0f, "dealId", emptyList());

        // when
        final int result = winningBidComparatorFactory.create(true).compare(higherPriceBidInfo, dealPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnZeroWhenBothHaveNonPgDeals() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId", emptyList());
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", emptyList());

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnZeroWhenBothHaveSamePgDealIdAndHasSamePrice() {
        // given
        final List<String> impDeals = singletonList("dealId");
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferDealsComparatorCompareShouldReturnMoreThanZeroWhenBothHaveSamePgDealIdAndFirstHasHigherPrice() {
        // given
        final List<String> impDeals = singletonList("dealId");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorShouldReturnLessThanZeroWhenFirstHasHigherPriceAndSecondHasLessImpPgDealIndex() {
        // given
        final List<String> impDeals = Arrays.asList("dealId1", "dealId2");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorShouldReturnLessThanZeroWhenFirstIsNonPgDealWithHigherPriceAndSecondPgDeal() {
        // given
        final List<String> impDeals = singletonList("dealId1");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferDealsComparatorShouldReturnGreaterThanZeroWhenFirstPgDealAndSecondMonPgDeal() {
        // given
        final List<String> impDeals = singletonList("dealId2");
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(10.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(true).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferDealsComparatorSortShouldReturnExpectedSortedResultWithDeals() {
        // given
        final String dealId1 = "pgDealId1";
        final String dealId2 = "pgDealId2";
        final List<String> impDeals = Arrays.asList(dealId1, dealId2);

        final BidInfo bidInfo1 = givenBidInfo(1.0f, dealId1, impDeals); // pg deal with lower price
        final BidInfo bidInfo2 = givenBidInfo(2.0f, dealId1, impDeals); // pg deal with middle price
        final BidInfo bidInfo3 = givenBidInfo(4.1f, dealId2, impDeals); // pg deal with higher price
        final BidInfo bidInfo4 = givenBidInfo(5.0f, null, impDeals); // non deal with lower price
        final BidInfo bidInfo5 = givenBidInfo(100.1f, null, impDeals); // non deal with higher price
        final BidInfo bidInfo6 = givenBidInfo(0.5f, "dealId1", impDeals); // non pg deal with lower price
        final BidInfo bidInfo7 = givenBidInfo(1f, "dealId2", impDeals); // non pg deal with middle price
        final BidInfo bidInfo8 = givenBidInfo(4.4f, "dealId3", impDeals); // non pg deal with higher price

        final List<BidInfo> bidInfos = Arrays.asList(bidInfo5, bidInfo3, bidInfo1, bidInfo2, bidInfo1, bidInfo4,
                bidInfo6, bidInfo7, bidInfo8);

        // when
        bidInfos.sort(winningBidComparatorFactory.create(true));

        // then
        assertThat(bidInfos).containsOnly(bidInfo4, bidInfo5, bidInfo6, bidInfo7, bidInfo8, bidInfo3, bidInfo2,
                bidInfo1, bidInfo1);
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
    public void preferPriceComparatorCompareShouldReturnLessThanZeroWhenFirstHasNonPgDeal() {
        // given
        final BidInfo dealPriceBidInfo = givenBidInfo(5.0f, "dealId", emptyList());
        final BidInfo higherPriceBidInfo = givenBidInfo(10.0f, null, emptyList());

        // when
        final int result = winningBidComparatorFactory.create(false).compare(dealPriceBidInfo, higherPriceBidInfo);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferPriceComparatorCompareShouldReturnGreaterThanZeroWhenFirstHasNoDeal() {
        // given
        final BidInfo higherPriceBidInfo = givenBidInfo(10.0f, null, emptyList());
        final BidInfo dealPriceBidInfo = givenBidInfo(5.0f, "dealId", emptyList());

        // when
        final int result = winningBidComparatorFactory.create(false).compare(higherPriceBidInfo, dealPriceBidInfo);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferPriceComparatorCompareShouldReturnZeroWhenBothHaveNonPgDeals() {
        // given
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId", emptyList());
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", emptyList());

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferPriceComparatorCompareShouldReturnZeroWhenBothHaveSamePgDealIdAndHasSamePrice() {
        // given
        final List<String> impDeals = singletonList("dealId");
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void preferPriceComparatorCompareShouldReturnMoreThanZeroWhenBothHaveSamePgDealIdAndFirstHasHigherPrice() {
        // given
        final List<String> impDeals = singletonList("dealId");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferPriceComparatorShouldReturnLessThanZeroWhenFirstHasHigherPriceAndSecondHasLessImpPgDealIndex() {
        // given
        final List<String> impDeals = Arrays.asList("dealId1", "dealId2");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferPriceComparatorShouldReturnLessThanZeroWhenFirstIsNonPgDealWithHigherPriceAndSecondPgDeal() {
        // given
        final List<String> impDeals = singletonList("dealId1");
        final BidInfo bidInfo1 = givenBidInfo(10.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(5.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isLessThan(0);
    }

    @Test
    public void preferPriceComparatorShouldReturnGreaterThanZeroWhenFirstPgDealAndSecondMonPgDeal() {
        // given
        final List<String> impDeals = singletonList("dealId2");
        final BidInfo bidInfo1 = givenBidInfo(5.0f, "dealId2", impDeals);
        final BidInfo bidInfo2 = givenBidInfo(10.0f, "dealId1", impDeals);

        // when
        final int result = winningBidComparatorFactory.create(false).compare(bidInfo1, bidInfo2);

        // then
        assertThat(result).isGreaterThan(0);
    }

    @Test
    public void preferPriceComparatorSortShouldReturnExpectedSortedResultWithDeals() {
        // given
        final String dealId1 = "pgDealId1";
        final String dealId2 = "pgDealId2";
        final List<String> impDeals = Arrays.asList(dealId1, dealId2);

        final BidInfo bidInfo1 = givenBidInfo(1.0f, dealId1, impDeals); // pg deal with lower price
        final BidInfo bidInfo2 = givenBidInfo(2.0f, dealId1, impDeals); // pg deal with middle price
        final BidInfo bidInfo3 = givenBidInfo(4.1f, dealId2, impDeals); // pg deal with higher price
        final BidInfo bidInfo4 = givenBidInfo(5.0f, null, impDeals); // non deal with lower price
        final BidInfo bidInfo5 = givenBidInfo(100.1f, null, impDeals); // non deal with higher price
        final BidInfo bidInfo6 = givenBidInfo(0.5f, "dealId1", impDeals); // non pg deal with lower price
        final BidInfo bidInfo7 = givenBidInfo(1f, "dealId2", impDeals); // non pg deal with middle price
        final BidInfo bidInfo8 = givenBidInfo(4.4f, "dealId3", impDeals); // non pg deal with higher price

        final List<BidInfo> bidInfos = Arrays.asList(bidInfo5, bidInfo3, bidInfo1, bidInfo2, bidInfo1, bidInfo4,
                bidInfo6, bidInfo7, bidInfo8);

        // when
        bidInfos.sort(winningBidComparatorFactory.create(false));

        // then
        assertThat(bidInfos).containsOnly(bidInfo6, bidInfo7, bidInfo8, bidInfo4, bidInfo5, bidInfo1,
                bidInfo1, bidInfo2, bidInfo3);
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

    private static BidInfo givenBidInfo(float price, String dealId, List<String> impDealIds) {
        final List<Deal> impDeals = impDealIds.stream()
                .map(impDealId -> Deal.builder().id(impDealId).build())
                .collect(Collectors.toList());
        final Pmp pmp = Pmp.builder().deals(impDeals).build();

        return BidInfo.builder()
                .bid(Bid.builder().impid(IMP_ID).price(BigDecimal.valueOf(price)).dealid(dealId).build())
                .correspondingImp(Imp.builder().id(IMP_ID).pmp(pmp).build())
                .build();
    }
}
