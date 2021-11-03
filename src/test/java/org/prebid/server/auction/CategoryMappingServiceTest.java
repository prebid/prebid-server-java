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
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class CategoryMappingServiceTest extends VertxTest {

    private static final PriceGranularity PRICE_GRANULARITY = PriceGranularity.DEFAULT;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    ApplicationSettings applicationSettings;

    private CategoryMappingService categoryMappingService;

    private Timeout timeout;

    @Before
    public void setUp() {
        categoryMappingService = new CategoryMappingService(applicationSettings, jacksonMapper);
        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500);
    }

    @Test
    public void applyCategoryMappingShouldReturnFilteredBidsWithCategory() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon",
                        givenBidderBid(givenBid("1", null, "9", singletonList("cat1")),
                                BidType.video, 10),
                        givenBidderBid(givenBid("2", null, "15", singletonList("cat2")),
                                BidType.video, 15)),
                givenBidderResponse("otherBid",
                        givenBidderBid(givenBid("3", null, "10", singletonList("cat3")),
                                BidType.video, 3),
                        givenBidderBid(givenBid("4", null, "15", singletonList("cat4")),
                                BidType.video, 1)));

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
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<Bid, String> resultCategories = new HashMap<>();

        resultCategories.put(givenBid("3", null, "10", singletonList("cat3")), "10.00_fetchedCatDup_5s");
        resultCategories.put(givenBid("4", null, "15", singletonList("cat4")), "15.00_fetchedCat4_5s");
        resultCategories.put(givenBid("2", null, "15", singletonList("cat2")), "15.00_fetchedCat2_15s");

        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(resultCategories);

        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids)
                .extracting(BidderBid::getBid).hasSize(3)
                .extracting(Bid::getId)
                .containsOnly("2", "3", "4");

        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: rubicon, bid ID: 1] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldTolerateBidsWithSameIdWithingDifferentBidders() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("1", null, "5", singletonList("cat2")),
                        BidType.video, 3)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<Bid, String> expectedBidCategory = new HashMap<>();
        expectedBidCategory.put(givenBid("1", null, "10", singletonList("cat1")), "10.00_fetchedCat1_10s");
        expectedBidCategory.put(givenBid("1", null, "5", singletonList("cat2")), "5.00_fetchedCat2_5s");
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, false);

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        verifyZeroInteractions(applicationSettings);
        assertThat(resultFuture.succeeded()).isTrue();
        final Map<Bid, String> expectedBidCategory = new HashMap<>();
        expectedBidCategory.put(givenBid("1", null, "10", singletonList("cat1")), "10.00_cat1_10s");
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(expectedBidCategory);
    }

    @Test
    public void applyCategoryMappingShouldReturnFailedFutureWhenTranslateTrueAndAdServerNull() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(null, "publisher", null, true, true);

        // when
        assertThatThrownBy(() -> categoryMappingService.createCategoryMapping(
                bidderResponses, BidRequest.builder().build(), extRequestTargeting, timeout))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Primary ad server required but was not defined when translate category is enabled");
    }

    @Test
    public void applyCategoryMappingShouldReturnFailedFutureWhenTranslateTrueAndAdServerIsThree() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(3, "publisher", null, true, true);

        // when and then
        assertThatThrownBy(() -> categoryMappingService.createCategoryMapping(
                bidderResponses, BidRequest.builder().build(), extRequestTargeting, timeout))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Primary ad server `3` is not recognized");
    }

    @Test
    public void applyCategoryMappingShouldReturnUseFreewheelAdServerWhenAdServerIs1() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        categoryMappingService.createCategoryMapping(bidderResponses, BidRequest.builder().build(), extRequestTargeting,
                timeout);

        // then
        verify(applicationSettings).getCategories(eq("freewheel"), anyString(), any());
    }

    @Test
    public void applyCategoryMappingShouldReturnUseDpfAdServerWhenAdServerIs2() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(2, "publisher",
                asList(10, 15, 5), true, true);

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        categoryMappingService.createCategoryMapping(bidderResponses, BidRequest.builder().build(), extRequestTargeting,
                timeout);

        // then
        verify(applicationSettings).getCategories(eq("dfp"), anyString(), any());
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithFailedCategoryFetch() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", singletonList("cat2")),
                        BidType.video, 3)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.failedFuture(new TimeoutException("Timeout")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Timeout");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithCatLengthMoreThanOne() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", asList("cat2-1", "cat2-2")),
                        BidType.video, 3)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid has more than one"
                        + " category");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidsWithWhenCatIsNull() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse(
                        "otherBid", givenBidderBid(givenBid("2", null, "5", null), BidType.video, 3)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid did not contain a"
                        + " category");
    }

    @Test
    public void applyCategoryMappingShouldRejectBidWhenNullCategoryReturnedFromSource() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", singletonList("cat2")),
                        BidType.video, 3)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(null));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Category mapping data for"
                        + " primary ad server: 'freewheel', publisher: 'publisher' not found");
    }

    @Test
    public void applyCategoryMappingShouldUseMediaTypePriceGranularityIfDefined() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.from(PRICE_GRANULARITY)))
                .mediatypepricegranularity(ExtMediaTypePriceGranularity.of(null,
                        mapper.valueToTree(ExtPriceGranularity.from(PriceGranularity.createFromString("low"))), null))
                .includebrandcategory(ExtIncludeBrandCategory.of(1, "publisher", true, true))
                .durationrangesec(asList(10, 15, 5))
                .build();

        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "5.00_fetchedCat1_10s"));
    }

    @Test
    public void applyCategoryMappingShouldRejectBidIfItsDurationLargerThanTargetingMax() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", singletonList("cat2")),
                        BidType.video, 20)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));
        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid duration '20' "
                        + "exceeds maximum '15'");
    }

    @Test
    public void applyCategoryMappingShouldReturnEmptyCategoryMappingResult() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, null)));

        final ExtRequestTargeting extRequestTargeting = ExtRequestTargeting.builder().build();

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result()).isEqualTo(
                CategoryMappingResult.of(
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        bidderResponses,
                        Collections.emptyList()));
    }

    @Test
    public void applyCategoryMappingShouldReturnFirstVideoCategoryIfPresent() {
        // given
        final List<BidderResponse> bidderResponses = List.of(
                givenBidderResponse(
                        "rubicon",
                        givenBidderBid(
                                givenBid("1", null, "10", singletonList("cat1"), "videoCategory"),
                                BidType.video,
                                null,
                                "videoCategory")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(
                1, "publisher", List.of(10, 15, 5), true, false);

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(
                Map.of(givenBid("1", null, "10", List.of("cat1"), "videoCategory"), "10.00_videoCategory_5s"));
    }

    @Test
    public void applyCategoryMappingShouldReturnEmptyCategoryIfNotWithCategory() {
        // given
        final List<BidderResponse> bidderResponses = List.of(
                givenBidderResponse(
                        "rubicon",
                        givenBidderBid(
                                givenBid("1", null, "10", singletonList("cat1"), "videoCategory"),
                                BidType.video,
                                null,
                                "videoCategory")));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(
                1, "publisher", List.of(10, 15, 5), false, false);

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(
                Map.of(givenBid("1", null, "10", List.of("cat1"), "videoCategory"), "10.00_5s"));
    }

    @Test
    public void applyCategoryMappingShouldReturnFirstIabBidCategoryIfWithCategoryAndNotTranslateCategories() {
        // given
        final List<BidderResponse> bidderResponses = List.of(
                givenBidderResponse(
                        "rubicon",
                        givenBidderBid(
                                givenBid("1", null, "10", List.of("cat1")),
                                BidType.video,
                                null,
                                null)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(
                1, "publisher", asList(10, 15, 5), true, false);

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(
                bidderResponses, BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(
                Map.of(givenBid("1", null, "10", List.of("cat1")), "10.00_cat1_5s"));
    }

    @Test
    public void applyCategoryMappingShouldReturnFetchedCategoryIfWithCategoryAndTranslateCategories() {
        // given
        final List<BidderResponse> bidderResponses = List.of(
                givenBidderResponse(
                        "rubicon",
                        givenBidderBid(
                                givenBid("1", null, "10", List.of("cat1")),
                                BidType.video,
                                null,
                                null)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(
                1, "publisher", asList(10, 15, 5), true, true);

        given(applicationSettings.getCategories(anyString(), anyString(), any()))
                .willReturn(Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories()).isEqualTo(
                Map.of(givenBid("1", null, "10", List.of("cat1")), "10.00_fetchedCat1_5s"));
    }

    @Test
    public void applyCategoryMappingShouldSetFirstDurationFromRangeIfDurationIsNull() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, null)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_5s"));
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByFetchedCategoryWhenWithCategoryIsTrue() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", singletonList("cat2")),
                        BidType.video, 4)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByBidCatWhenWithCategoryIsTrueAndTranslateFalse() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "5", singletonList("cat1")),
                        BidType.video, 4)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), true, false);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_cat1_10s"));
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .containsOnly("Bid rejected [bidder: otherBid, bid ID: 2] with a reason: Bid was deduplicated");
    }

    @Test
    public void applyCategoryMappingShouldDeduplicateBidsByPriceAndDurationIfWithCategoryFalse() {
        // given
        final List<BidderResponse> bidderResponses = asList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)),
                givenBidderResponse("otherBid", givenBidderBid(givenBid("2", null, "10", singletonList("cat2")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher",
                asList(10, 15, 5), false, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")),
                Future.succeededFuture(singletonMap("cat2", "fetchedCat2")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories().entrySet())
                .extracting(Map.Entry::getValue)
                .containsExactly("10.00_10s");
        assertThat(resultFuture.result().getErrors()).hasSize(1)
                .allMatch(error -> error.contains("Bid was deduplicated"));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndFetchedCategoryAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndBidCatAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, false);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_cat1_10s"));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndBidCatAndDurationAndBidder() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, false)
                .toBuilder().appendbiddernames(true).build();
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_cat1_10s_rubicon"));
    }

    @Test
    public void applyCategoryMappingShouldReturnDurCatBuiltFromPriceAndDuration() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", null, "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")), "10.00_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "rubiconPrefix3_10s"));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldUseDealTierFromImpExtPrebidBidders() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(mapper.valueToTree(ExtImp.of(ExtImpPrebid.builder()
                                .bidder(mapper.createObjectNode()
                                        .set("rubicon", givenDealTier("prebidPrefix", 4))).build(), null)))
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "prebidPrefix4_10s"));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldPrecedencePriorityAndDurationFromPrebidOverFromImpExt() {
        // given
        final ExtImp extImp = ExtImp.of(ExtImpPrebid.builder()
                .bidder(mapper.createObjectNode().set("rubicon", givenDealTier("prebidPrefix", 4))).build(), null);
        final ObjectNode impExt = mapper.valueToTree(extImp);
        impExt.set("rubicon", givenDealTier("extPrefix", 3));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(impExt)
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), false, null);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "prebidPrefix4_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "rubiconPrefix3_fetchedCat1_10s"));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getBiddersToBidsSatisfiedPriority())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")), true));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
        assertThat(resultFuture.result().getBidderResponses())
                .extracting(BidderResponse::getSeatBid)
                .flatExtracting(BidderSeatBid::getBids).hasSize(1);
        assertThat(resultFuture.result().getBiddersToBidsSatisfiedPriority())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")), false));
        assertThat(resultFuture.result().getErrors()).isEmpty();
    }

    @Test
    public void applyCategoryMappingShouldIgnoreContextAndPrebidInImpExt() {
        // given
        final ObjectNode impExt = mapper.createObjectNode().set("rubicon", givenDealTier("rubiconPrefix", 3));
        impExt.set("context", mapper.createObjectNode());
        impExt.set("prebid", mapper.createObjectNode());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("impId1")
                        .ext(impExt)
                        .build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder().supportdeals(true).build())).build();

        final List<BidderResponse> bidderResponses = singletonList(
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "rubiconPrefix3_fetchedCat1_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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
                givenBidderResponse("rubicon", givenBidderBid(givenBid("1", "impId1", "10", singletonList("cat1")),
                        BidType.video, 10)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                bidRequest, extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", "impId1", "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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
                        givenBidderBid(givenBid("1", null, "10", singletonList("cat1")), BidType.video, 10)),
                givenBidderResponse("otherBidder",
                        givenBidderBid(givenBid("2", null, "10", null), BidType.video, 10),
                        givenBidderBid(givenBid("3", null, "10", singletonList("cat1")), BidType.video, 30)));

        final ExtRequestTargeting extRequestTargeting = givenTargeting(1, "publisher", asList(10, 15, 5), true, true);
        given(applicationSettings.getCategories(anyString(), anyString(), any())).willReturn(
                Future.succeededFuture(singletonMap("cat1", "fetchedCat1")));

        // when
        final Future<CategoryMappingResult> resultFuture = categoryMappingService.createCategoryMapping(bidderResponses,
                BidRequest.builder().build(), extRequestTargeting, timeout);

        // then
        assertThat(resultFuture.succeeded()).isTrue();
        assertThat(resultFuture.result().getBiddersToBidsCategories())
                .isEqualTo(Collections.singletonMap(givenBid("1", null, "10", singletonList("cat1")),
                        "10.00_fetchedCat1_10s"));
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

    private static BidderBid givenBidderBid(Bid bid, BidType bidType, Integer duration) {
        return BidderBid.of(bid, bidType, null, 5, ExtBidPrebidVideo.of(duration, null));
    }

    private static BidderBid givenBidderBid(Bid bid, BidType bidType, Integer duration, String primaryCategory) {
        return BidderBid.of(bid, bidType, null, 5, ExtBidPrebidVideo.of(duration, primaryCategory));
    }

    private static Bid givenBid(String bidId, String impId, String price, List<String> cat) {
        return Bid.builder()
                .id(bidId)
                .impid(impId)
                .cat(cat)
                .price(new BigDecimal(price))
                .build();
    }

    private static Bid givenBid(String bidId, String impId, String price, List<String> cat, String videoCategory) {
        return givenBid(bidId, impId, price, cat).toBuilder()
                .ext(mapper.valueToTree(
                        ExtBidPrebid.builder()
                                .video(ExtBidPrebidVideo.of(null, videoCategory))
                                .build()))
                .build();
    }

    private static ExtRequestTargeting givenTargeting(Integer primaryAdServer,
                                                      String publisher,
                                                      List<Integer> durations,
                                                      Boolean withCategory,
                                                      Boolean translateCategories) {
        return ExtRequestTargeting.builder()
                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.from(PRICE_GRANULARITY)))
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
