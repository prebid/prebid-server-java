package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cache.model.CacheHttpCall;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.CacheHttpResponse;
import org.prebid.server.cache.model.CacheIdInfo;
import org.prebid.server.cache.model.CacheServiceResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.response.CacheAsset;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseCache;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class BidResponseCreatorTest extends VertxTest {

    private static final String CACHE_HOST = "testHost";
    private static final String CACHE_PATH = "testPath";
    private static final String CACHE_ASSET = "uuid=";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private BidResponseCreator bidResponseCreator;

    @Before
    public void setUp() {
        bidResponseCreator = new BidResponseCreator(bidderCatalog, CACHE_HOST, CACHE_PATH, CACHE_ASSET);
    }

    @Test
    public void shouldSetExpectedConstantResponseFields() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses =
                singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        final BidResponse responseWithExpectedFields = BidResponse.builder()
                .id("123")
                .cur("USD")
                .ext(mapper.valueToTree(
                        ExtBidResponse.of(null, null, singletonMap("bidder1", 100), 1000L, null)))
                .build();

        assertThat(bidResponse)
                .isEqualToIgnoringGivenFields(responseWithExpectedFields, "nbr", "seatbid");
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesAreEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(emptyList(), bidRequest, null,
                cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);
    }

    @Test
    public void shouldSetNbrValueTwoAndEmptySeatbidWhenIncomingBidResponsesDoNotContainAnyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses =
                singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null,
                cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse).returns(2, BidResponse::getNbr);
        assertThat(bidResponse).returns(emptyList(), BidResponse::getSeatbid);
    }

    @Test
    public void shouldSetNbrNullAndPopulateSeatbidWhenAtLeastOneBidIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses =
                singletonList(BidderResponse.of("bidder1", givenSeatBid(
                        BidderBid.of(Bid.builder().build(), null, null)), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest, null,
                cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getNbr()).isNull();
        assertThat(bidResponse.getSeatbid()).hasSize(1);
    }

    @Test
    public void shouldSkipBidderResponsesWhereSeatBidContainEmptyBids() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(), 0),
                BidderResponse.of("bidder2",
                        givenSeatBid(BidderBid.of(Bid.builder().build(), banner, "USD")), 0));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1);
    }

    @Test
    public void shouldSetExpectedResponseSeatBidAndBidFields() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(Bid.builder().id("bidId").price(BigDecimal.ONE).adm("adm")
                        .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).containsOnly(SeatBid.builder()
                .seat("bidder1")
                .group(0)
                .bid(singletonList(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .adm("adm")
                        .ext(mapper.valueToTree(ExtPrebid.of(
                                ExtBidPrebid.of(banner, null, null, null), singletonMap("bidExt", 1))))
                        .build()))
                .build());
    }

    @Test
    public void shouldSetBidAdmToNullIfCacheIdIsPresentAndReturnCreativeBidsIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid winnerBid = Bid.builder().price(BigDecimal.ONE).adm("adm").build();
        final CacheServiceResult cacheServiceResult =
                givenCacheServiceResult(singletonMap(winnerBid, CacheIdInfo.of("id", null)));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();
    }

    @Test
    public void shouldSetBidAdmToNullIfVideoCacheIdIsPresentAndReturnCreativeVideoBidsIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid winnerBid = Bid.builder().price(BigDecimal.ONE).adm("adm").build();
        final CacheServiceResult cacheServiceResult =
                givenCacheServiceResult(singletonMap(winnerBid, CacheIdInfo.of("id", null)));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(Bid::getAdm)
                .containsNull();
    }

    @Test
    public void shouldTolerateMissingExtInSeatBidAndBid() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(
                        Bid.builder().id("bidId").price(BigDecimal.ONE).build(), banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .containsOnly(Bid.builder()
                        .id("bidId")
                        .price(BigDecimal.ONE)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.of(banner, null, null, null), null)))
                        .build());
    }

    @Test
    public void shouldPopulateTargetingKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final List<BidderResponse> bidderResponses = singletonList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(firstBid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder", "bidder1"), // winning bid through all bids
                        tuple("hb_bidder_bidder1", "bidder1")); // winning bid for separate bidder
    }

    @Test
    public void shouldPopulateTargetingKeywordsForWinningBidsAndWinningBidsByBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final Bid firstBid = Bid.builder().id("bidId1").price(BigDecimal.valueOf(5.67)).build();
        final Bid thirdBid = Bid.builder().id("bidId3").price(BigDecimal.valueOf(7.25)).build();
        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1",
                        givenSeatBid(BidderBid.of(firstBid, banner, null), BidderBid.of(
                                Bid.builder().id("bidId2").price(BigDecimal.valueOf(4.98)).build(), banner, null)),
                        100),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(thirdBid, banner, null)), 111));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(3)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"), // winning bid through all bids
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"), // winning bid for separate bidder
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder2")) // winning bid for separate bidder
                .containsOnly(
                        tuple("bidId1", null, "bidder1", null),
                        tuple("bidId2", null, null, null),
                        tuple("bidId3", "bidder2", null, "bidder2"));
    }

    @Test
    public void shouldPopulateTargetingKeywordsFromMediaTypePriceGranularities() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), ExtMediaTypePriceGranularity.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(3, singletonList(
                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(1))))), null, null),
                null, true, true);

        final Bid winnerBid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.000"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_pb_bidder1", "5.000"),
                        tuple("hb_bidder_bidder1", "bidder1"));
    }

    @Test
    public void shouldPopulateCacheIdHostPathAndUuidTargetingKeywords() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid winnerBid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final CacheServiceResult cacheServiceResult = givenCacheServiceResult(
                singletonMap(winnerBid, CacheIdInfo.of("cacheId", "videoId")));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid).hasSize(1)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .flatExtracting(Map::entrySet)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("hb_pb", "5.00"),
                        tuple("hb_bidder", "bidder1"),
                        tuple("hb_cache_id", "cacheId"),
                        tuple("hb_uuid", "videoId"),
                        tuple("hb_cache_host", "testHost"),
                        tuple("hb_cache_path", "testPath"),
                        tuple("hb_pb_bidder1", "5.00"),
                        tuple("hb_bidder_bidder1", "bidder1"),
                        tuple("hb_cache_id_bidder1", "cacheId"),
                        tuple("hb_uuid_bidder1", "videoId"),
                        tuple("hb_cache_host_bidder1", "testHost"),
                        tuple("hb_cache_path_bidder1", "testPath"));
    }

    @Test
    public void shouldReturnCacheEntityInExt() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid winnerBid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final CacheServiceResult cacheServiceResult = givenCacheServiceResult(
                singletonMap(winnerBid, CacheIdInfo.of("cacheId", "videoId")));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(extractedBid -> toExtPrebid(extractedBid.getExt()).getPrebid().getCache())
                .extracting(ExtResponseCache::getBids, ExtResponseCache::getVastXml)
                .containsExactly(tuple(
                        CacheAsset.of("uuid=cacheId", "cacheId"),
                        CacheAsset.of("uuid=videoId", "videoId")));
    }

    @Test
    public void shouldNotPopulateWinningBidTargetingIfIncludeWinnersFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, false, true);

        final Bid winnerBid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final CacheServiceResult cacheServiceResult = givenCacheServiceResult(
                singletonMap(winnerBid, CacheIdInfo.of("cacheId", "videoId")));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", null, "bidder1"));
    }

    @Test
    public void shouldNotPopulateBidderKeysTargetingIfIncludeBidderKeysFlagIsFalse() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, true, false);

        final Bid winnerBid = Bid.builder().id("bidId").price(BigDecimal.valueOf(5.67)).build();
        final CacheServiceResult cacheServiceResult = givenCacheServiceResult(
                singletonMap(winnerBid, CacheIdInfo.of("cacheId", "videoId")));

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                givenSeatBid(BidderBid.of(winnerBid, banner, "USD")), 100));

        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().shouldCacheBids(true).shouldCacheVideoBids(true).build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(
                        Bid::getId,
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_bidder_bidder1"))
                .containsOnly(
                        tuple("bidId", "bidder1", null));
    }

    @Test
    public void shouldNotPopulateCacheIdTargetingKeywordsIfBidCpmIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final ExtRequestTargeting targeting = givenTargeting();

        final Bid bid1 = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ZERO).build();
        final Bid bid2 = Bid.builder().id("bidId2").impid("impId2").price(BigDecimal.valueOf(5.67)).build();

        final List<BidderResponse> bidderResponses = asList(
                BidderResponse.of("bidder1", givenSeatBid(BidderBid.of(bid1, banner, null)), 99),
                BidderResponse.of("bidder2", givenSeatBid(BidderBid.of(bid2, banner, null)), 123));

        final CacheServiceResult cacheServiceResult = CacheServiceResult.of(null, null,
                singletonMap(bid2, CacheIdInfo.of("cacheId2", null)));
        final BidRequestCacheInfo cacheInfo = BidRequestCacheInfo.builder().build();

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                targeting, cacheServiceResult, cacheInfo, emptyMap(), false);

        // then
        assertThat(bidResponse.getSeatbid()).flatExtracting(SeatBid::getBid).hasSize(2)
                .extracting(
                        bid -> toTargetingByKey(bid, "hb_bidder"),
                        bid -> toTargetingByKey(bid, "hb_cache_id"),
                        bid -> toTargetingByKey(bid, "hb_cache_id_bidder2"))
                .containsOnly(
                        tuple("bidder1", null, null),
                        tuple("bidder2", "cacheId2", "cacheId2"));
    }

    @Test
    public void shouldPopulateBidResponseExtension() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = CacheServiceResult.of(
                CacheHttpCall.of(null, null, 666), new RuntimeException("cacheError"), null);

        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("bidder1",
                BidderSeatBid.of(emptyList(), null, singletonList(BidderError.badInput("bad_input"))), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNull();
        assertThat(responseExt.getUsersync()).isNull();
        assertThat(responseExt.getTmaxrequest()).isEqualTo(1000L);

        assertThat(responseExt.getErrors()).hasSize(2)
                .containsOnly(entry("bidder1", singletonList(ExtBidderError.of(2, "bad_input"))),
                        entry("prebid", singletonList(ExtBidderError.of(999, "cacheError"))));

        assertThat(responseExt.getResponsetimemillis()).hasSize(2)
                .containsOnly(entry("bidder1", 100), entry("cache", 666));
    }

    @Test
    public void shouldProcessRequestAndAddErrorAboutDeprecatedBidder() {
        // given
        final String invalidBidderName = "invalid";

        final BidRequest bidRequest = givenBidRequest(Imp.builder()
                .ext(mapper.valueToTree(singletonMap(invalidBidderName, 0)))
                .build());
        final CacheServiceResult cacheServiceResult = givenEmptyCacheServiceResult();

        final List<BidderResponse> bidderResponses =
                singletonList(BidderResponse.of("bidder1", givenSeatBid(), 100));

        given(bidderCatalog.isDeprecatedName(invalidBidderName)).willReturn(true);
        given(bidderCatalog.errorForDeprecatedName(invalidBidderName)).willReturn(
                "invalid has been deprecated and is no longer available. Use valid instead.");

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), false);

        // then
        assertThat(bidResponse.getExt()).isEqualTo(
                mapper.valueToTree(ExtBidResponse.of(null, singletonMap(
                        invalidBidderName, singletonList(ExtBidderError.of(BidderError.Type.bad_input.getCode(),
                                "invalid has been deprecated and is no longer available. Use valid instead."))),
                        singletonMap("bidder1", 100), 1000L, null)));
    }

    @Test
    public void shouldPopulateBidResponseDebugExtensionIfDebugIsEnabled() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest();
        final CacheServiceResult cacheServiceResult = CacheServiceResult.of(
                CacheHttpCall.of(CacheHttpRequest.of("test.uri", null),
                        CacheHttpResponse.of(500, null), null),
                null, null);

        final List<BidderResponse> bidderResponses =
                singletonList(BidderResponse.of("bidder1", BidderSeatBid.of(
                        emptyList(), singletonList(ExtHttpCall.builder().status(200).build()), null), 100));

        // when
        final BidResponse bidResponse = bidResponseCreator.create(bidderResponses, bidRequest,
                null, cacheServiceResult, null, emptyMap(), true);

        // then
        final ExtBidResponse responseExt = mapper.treeToValue(bidResponse.getExt(), ExtBidResponse.class);

        assertThat(responseExt.getDebug()).isNotNull();
        assertThat(responseExt.getDebug().getHttpcalls()).hasSize(2)
                .containsOnly(entry("bidder1", singletonList(ExtHttpCall.builder().status(200).build())),
                        entry("cache", singletonList(ExtHttpCall.builder().uri("test.uri").status(500).build())));

        assertThat(responseExt.getDebug().getResolvedrequest()).isEqualTo(bidRequest);
    }

    private static CacheServiceResult givenEmptyCacheServiceResult() {
        return givenCacheServiceResult(emptyMap());
    }

    private static CacheServiceResult givenCacheServiceResult(Map<Bid, CacheIdInfo> cacheBids) {
        return CacheServiceResult.of(null, null, cacheBids);
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .id("123")
                .cur(singletonList("USD"))
                .tmax(1000L)
                .imp(asList(imps))
                .build();
    }

    private static BidderSeatBid givenSeatBid(BidderBid... bids) {
        return BidderSeatBid.of(asList(bids), emptyList(), emptyList());
    }

    private static ExtRequestTargeting givenTargeting() {
        return ExtRequestTargeting.of(Json.mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))), null, null, true, true);
    }

    private static ExtPrebid<ExtBidPrebid, ?> toExtPrebid(ObjectNode ext) {
        try {
            return mapper.readValue(mapper.treeAsTokens(ext), new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            });
        } catch (IOException e) {
            return rethrow(e);
        }
    }

    private static String toTargetingByKey(Bid bid, String targetingKey) {
        final Map<String, String> targeting = toExtPrebid(bid.getExt()).getPrebid().getTargeting();
        return targeting != null ? targeting.get(targetingKey) : null;
    }
}