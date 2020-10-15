package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.settings.ApplicationSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class CategoryMapperTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    ApplicationSettings applicationSettings;

    private Timeout timeout;

    private CategoryMapper categoryMapper;

    private static PriceGranularity priceGranularity;

    @Before
    public void setUp() {
        categoryMapper = new CategoryMapper(applicationSettings, jacksonMapper);
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
        priceGranularity = PriceGranularity.DEFAULT;
    }

    @Test
    public void applyCategoryMappingShouldReturnFilteredBidsWithCategory() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1"),
                        givenBidderBid("2", null, "15", BidType.video, singletonList("cat2"), 15, "prCategory2")),
                givenBidderResponse("otherBid", givenBidderBid("3", null, "10", BidType.video, singletonList("cat3"), 3,
                        "prCategory3"),
                        givenBidderBid("4", null, "15", BidType.video, singletonList("cat4"), 1, "prCategory4")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        // first and third fetch will have conflict, so one bid should be filtered in result
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCatDup")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")),
                // id for cat 3 is the same as for cat1, that will cause duplication
                Future.succeededFuture(singletonMap("cat3", "fetchedCatDup")),
                Future.succeededFuture(singletonMap("cat4", "fetchedCat4")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<String, Map<String, String>> expectedBidCategory = new HashMap<>();
        final Map<String, String> rubiconBidToCategory = new HashMap<>();
        rubiconBidToCategory.put("1", "10.00_fetchedCatDup_10s");
        rubiconBidToCategory.put("2", "15.00_fetchedCat2_15s");
        expectedBidCategory.put("rubicon", rubiconBidToCategory);
        expectedBidCategory.put("otherBid", Collections.singletonMap("4", "15.00_fetchedCat4_5s"));
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(expectedBidCategory);
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid).hasSize(3)
                .extracting(Bid::getId)
                .containsOnly("1", "2", "4");
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 3] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldTolerateBidsWithSameIdWithingDifferentBidders() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("1", null, "5", BidType.video, singletonList("cat2"), 3,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<String, Map<String, String>> expectedBidCategory = new HashMap<>();
        expectedBidCategory.put("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s"));
        expectedBidCategory.put("otherBid", Collections.singletonMap("1", "5.00_fetchedCat2_5s"));
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(expectedBidCategory);
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid).hasSize(2)
                .extracting(Bid::getId)
                .containsOnly("1", "1");
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldNotCallFetchCategoryWhenTranslateCategoriesFalse() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, false);

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        verifyZeroInteractions(applicationSettings);
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<String, Map<String, String>> expectedBidCategory = new HashMap<>();
        expectedBidCategory.put("rubicon", Collections.singletonMap("1", "10.00_cat1_10s"));
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(expectedBidCategory);
    }

    @Test
    public void applyCategoryMappingShouldReturnFailedFutureWhenTranslateTrueAndAdServerNull() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(null, "publisher", null, true, true);

        // when
        final Future<CategoryMappingResult> categoryMappingResultFuture =
                categoryMapper.createCategoryMapping(bidderResponses, BidRequest.builder().build(),
                        extRequestTargeting, timeout);

        // then
        assertThat(categoryMappingResultFuture.failed()).isTrue();
        assertThat(categoryMappingResultFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Primary ad server required but was not defined when translate category is enabled");
    }

    @Test
    public void applyCategoryMappingShouldReturnFailedFutureWhenTranslateTrueAndAdServerIsThree() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(3, "publisher", null, true, true);

        // when
        final Future<CategoryMappingResult> categoryMappingResultFuture = categoryMapper.createCategoryMapping(
                bidderResponses, BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(categoryMappingResultFuture.failed()).isTrue();
        assertThat(categoryMappingResultFuture.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Primary ad server `3` is not recognized");
    }

    @Test
    public void applyCategoryMappingShouldReturnUseFreewheelAdServerWhenAdServerIs1() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        categoryMapper.createCategoryMapping(bidderResponses, BidRequest.builder().build(), extRequestTargeting,
                timeout);

        // then
        verify(applicationSettings).getCategories(eq("freewheel"), anyString(), any());
    }

    @Test
    public void applyCategoryMappingShouldReturnUseDpfAdServerWhenAdServerIs2() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(2, "publisher",
                asList(10, 15, 5), true, true);

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        categoryMapper.createCategoryMapping(bidderResponses, BidRequest.builder().build(), extRequestTargeting,
                timeout);

        // then
        verify(applicationSettings).getCategories(eq("dfp"), anyString(), any());
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithFailedCategoryFetch() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, singletonList("cat2"), 3,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.failedFuture(new TimeoutException("Timeout")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Timeout");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithCatLengthMoreThanOne() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video,
                        asList("cat2-1", "cat2-2"), 3, "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid has more than one"
                        + " category");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithWhenCatIsNull() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, null, 3,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid did not contain a"
                        + " category");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidWhenNullCategoryReturnedFromSource() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, singletonList("cat2"), 3,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(null));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Category mapping data for"
                        + " primary ad server: 'freewheel', publisher: 'publisher' not found");
    }

    @Test
    public void applyCategoryMappingShouldUseMediaTypePriceGranularityIfDefined() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.from(priceGranularity)))
                .mediatypepricegranularity(ExtMediaTypePriceGranularity.of(null,
                        mapper.valueToTree(ExtPriceGranularity.from(PriceGranularity.createFromString("low"))), null))
                .includebrandcategory(ExtIncludeBrandCategory.of(1, "publisher", true, true))
                .durationrangesec(asList(10, 15, 5))
                .build();

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "5.00_fetchedCat1_10s")));
    }

    @Test
    public void applyCategoryMappingShouldRejectBidIfItsDurationLargerThanTargetingMax() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, singletonList("cat2"), 20,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid duration '20' "
                        + "exceeds maximum '15'");
    }

    @Test
    public void applyCategoryMappingShouldSetFirstDurationFromRangeIfDurationIsNull() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"),
                        null, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_5s")));
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByFetchedCategoryWhenWithCategoryIsTrue() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, singletonList("cat2"), 4,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByBidCatWhenWithCategoryIsTrueAndTranslateFalse() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "5", BidType.video, singletonList("cat1"), 4,
                        "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, false);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_cat1_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByPriceAndDurationIfWithCategoryFalse() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")),
                givenBidderResponse("otherBid", givenBidderBid("2", null, "10", BidType.video, singletonList("cat2"),
                        10, "prCategory2")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), false, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("otherBid", Collections.singletonMap("2", "10.00_10s")));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: rubicon, bid ID: 1] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndFetchedCategoryAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndBidCatAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, false);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_cat1_10s")));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10,
                        "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "10.00_10s")));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriorityAndDuration() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier("rubiconPrefix", 3)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon", Collections.singletonMap("1", "rubiconPrefix3_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriorityCatAndDuration() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier("rubiconPrefix", 3)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "rubiconPrefix3_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldRemoveSpacesFromDealTierPrefixInCatDur() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier(" ru bic onP refix", 3)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "rubiconPrefix3_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldBuildCatDurFromPriceCatAndDurIfSupportDealsFalse() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier("rubiconPrefix", 3)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(false).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldBuildCatDurFromPriceCatAndDurIfBidPriorityLowerThanDealTier() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier("rubiconPrefix", 10)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldIgnoreContextAndPrebidInImpExt() {
        // given
        final ObjectNode impExt = mapper.createObjectNode().set("rubicon",
                givenDealTier("rubiconPrefix", 3));
        impExt.set("context", mapper.createObjectNode());
        impExt.set("prebid", mapper.createObjectNode());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(impExt)
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "rubiconPrefix3_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldAddErrorIfImpBidderDoesNotHaveDealTierAndCreateRegularCatDur() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", mapper.createObjectNode()))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("DealTier configuration not defined for bidder 'rubicon', imp ID 'impId1'");
    }

    @Test
    public void applyCategoryMappingShouldAddErrorIfPrefixIsNullAndCreateRegularCatDur() {
        // given
        final JsonNode dealTier = mapper.createObjectNode().set("dealTier",
                mapper.createObjectNode().put("minDealTier", 3));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", dealTier))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("DealTier configuration not valid for bidder 'rubicon', imp ID 'impId1' "
                        + "with a reason: dealTier.prefix empty string or null");
    }

    @Test
    public void applyCategoryMappingShouldAddErrorIfMinDealTierIsNullAndCreateRegularCatDur() {
        // given
        final JsonNode dealTier = mapper.createObjectNode().set("dealTier",
                mapper.createObjectNode().put("prefix", "rubiconPrefix"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", dealTier))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("DealTier configuration not valid for bidder 'rubicon', imp ID 'impId1' with a reason:"
                        + " dealTier.minDealTier should be larger than 0, but was null");
    }

    @Test
    public void applyCategoryMappingShouldAddErrorIfMinDealTierLessThanZeroAndCreateRegularCatDur() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.createObjectNode().set("rubicon", givenDealTier("prefix", -1)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid("1", "impId1", "10", BidType.video, singletonList("cat1"),
                        10, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("DealTier configuration not valid for bidder 'rubicon', imp ID 'impId1' with a reason:"
                        + " dealTier.minDealTier should be larger than 0, but was -1");
    }

    @Test
    public void applyCategoryMappingShouldRejectAllBidsFromBidderInDifferentReasons() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon",
                        givenBidderBid("1", null, "10", BidType.video, singletonList("cat1"), 10, "prCategory1")),
                givenBidderResponse("otherBidder",
                        givenBidderBid("2", null, "10", BidType.video, null, 10, "prCategory1"),
                        givenBidderBid("3", null, "10", BidType.video, singletonList("cat1"), 30, "prCategory1")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMapper.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap("rubicon",
                        Collections.singletonMap("1", "10.00_fetchedCat1_10s")));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid).hasSize(1)
                .extracting(Bid::getId)
                .containsOnly("1");
        assertThat(resultFuture.result().getErrors()).hasSize(2)
                .containsOnly("Bid rejected [bidder: otherBidder, bid ID: 2] with a reason: Bid did not contain "
                                + "a category",
                        "Bid rejected [bidder: otherBidder, bid ID: 3] with a reason: Bid duration '30' exceeds"
                                + " maximum '15'");
    }

    private static BidderResponse givenBidderResponse(String bidder, BidderBid... bidderBids) {
        return BidderResponse.of(bidder, BidderSeatBid.of(asList(bidderBids), null, null), 100);
    }

    private static BidderBid givenBidderBid(String bidId, String impId, String price, BidType bidType, List<String> cat,
                                            Integer duration, String primaryCategory) {
        return BidderBid.of(
                Bid.builder()
                        .id(bidId)
                        .impid(impId)
                        .cat(cat)
                        .price(new BigDecimal(price))
                        .build(),
                bidType, null, 5, ExtBidPrebidVideo.of(duration, primaryCategory));
    }

    private static ExtRequestTargeting givenTargeting(Integer primaryAdServer,
                                                      String publisher,
                                                      List<Integer> durations,
                                                      Boolean withCategory,
                                                      Boolean translateCategories) {
        return ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.from(priceGranularity)))
                .includebrandcategory(ExtIncludeBrandCategory.of(primaryAdServer, publisher, withCategory,
                        translateCategories))
                .durationrangesec(durations)
                .build();
    }

    private static JsonNode givenDealTier(String prefix, Integer minDealTier) {
        return mapper.createObjectNode().set("dealTier",
                mapper.createObjectNode().put("prefix", prefix).put("minDealTier", minDealTier));
    }
}
