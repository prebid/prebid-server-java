package org.prebid.server.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Imp.ImpBuilder;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import org.apache.commons.collections4.MapUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRequestCacheInfo;
import org.prebid.server.auction.model.BidderPrivacyResult;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidderError;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.settings.model.Account;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class ExchangeServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private Usersyncer usersyncer;
    @Mock
    private StoredResponseProcessor storedResponseProcessor;
    @Mock
    private BidRequester bidRequester;
    @Mock
    private FpdResolver fpdResolver;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private BidResponseCreator bidResponseCreator;
    @Spy
    private BidResponsePostProcessor.NoOpBidResponsePostProcessor bidResponsePostProcessor;
    @Mock
    private Metrics metrics;
    @Mock
    private UidsCookie uidsCookie;

    private Clock clock;

    private ExchangeService exchangeService;

    private Timeout timeout;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        given(bidResponseCreator.create(anyList(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(singletonList(givenBid(identity())))));

        given(bidderCatalog.isValidName(anyString())).willReturn(true);
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.usersyncerByName(anyString())).willReturn(usersyncer);

        given(privacyEnforcementService.mask(any(), argThat(MapUtils::isNotEmpty), any(), any()))
                .willAnswer(inv ->
                        Future.succeededFuture(((Map<String, User>) inv.getArgument(1)).entrySet().stream()
                                .map(bidderAndUser -> BidderPrivacyResult.builder()
                                        .requestBidder(bidderAndUser.getKey())
                                        .user(bidderAndUser.getValue())
                                        .build())
                                .collect(Collectors.toList())));

        given(privacyEnforcementService.mask(any(), argThat(MapUtils::isEmpty), any(), any()))
                .willReturn(Future.succeededFuture(emptyList()));

        given(fpdResolver.resolveUser(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveSite(any(), any())).willAnswer(invocation -> invocation.getArgument(0));
        given(fpdResolver.resolveApp(any(), any())).willAnswer(invocation -> invocation.getArgument(0));

        given(usersyncer.getCookieFamilyName()).willReturn("cookieFamily");

        given(bidRequester.waitForBidResponses(any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any()))
                .willAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willAnswer(inv -> Future.succeededFuture(StoredResponseResult.of(inv.getArgument(0), emptyList())));
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willAnswer(inv -> inv.getArgument(0));

        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500);

        exchangeService = new ExchangeService(
                bidderCatalog,
                storedResponseProcessor,
                bidRequester,
                privacyEnforcementService,
                fpdResolver,
                bidResponseCreator,
                bidResponsePostProcessor,
                metrics,
                clock,
                jacksonMapper);
    }

    @Test
    public void shouldTolerateImpWithoutExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(null));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verifyZeroInteractions(bidderCatalog);
        assertThat(result).extracting(AuctionContext::getBidResponse).isNotNull();
    }

    @Test
    public void shouldTolerateImpWithUnknownBidderInExtension() {
        // given
        given(bidderCatalog.isValidName(anyString())).willReturn(false);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("invalid", 0)));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidderCatalog).isValidName(eq("invalid"));
        assertThat(result).extracting(AuctionContext::getBidResponse).isNotNull();
    }

    @Test
    public void shouldExtractRequestWithBidderSpecificExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1).first()
                .extracting(auctionParticipation -> auctionParticipation.getBidderRequest().getBidRequest())
                .containsOnly(BidRequest.builder()
                        .id("requestId")
                        .cur(singletonList("USD"))
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                                .build()))
                        .tmax(500L)
                        .build());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldExtractRequestWithCurrencyRatesExtension() {
        // given
        final Map<String, Map<String, BigDecimal>> currencyRates = doubleMap(
                "GBP", singletonMap("EUR", BigDecimal.valueOf(1.15)),
                "UAH", singletonMap("EUR", BigDecimal.valueOf(1.1565)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder
                        .id("requestId")
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder()
                                        .currency(ExtRequestCurrency.of(currencyRates, false))
                                        .build()))
                        .tmax(500L));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<List<AuctionParticipation>> auctionParticipationsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(bidRequester).waitForBidResponses(auctionParticipationsCaptor.capture(), any(), anyBoolean(),
                anyBoolean(), any(), any(), eq(currencyRates), any());

        final List<AuctionParticipation> auctionParticipations = auctionParticipationsCaptor.getValue();
        assertThat(auctionParticipations).hasSize(1).first()
                .extracting(auctionParticipation -> auctionParticipation.getBidderRequest().getBidRequest())
                .containsOnly(BidRequest.builder()
                        .id("requestId")
                        .cur(singletonList("USD"))
                        .imp(singletonList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                                .build()))
                        .ext(ExtRequest.of(
                                ExtRequestPrebid.builder().currency(ExtRequestCurrency.of(currencyRates, false))
                                        .build()))
                        .tmax(500L)
                        .build());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldExtractMultipleRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), identity()),
                givenImp(singletonMap("bidder1", 3), identity())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest().getImp())
                .extracting(imps -> imps.stream().map(imp -> imp.getExt().get("bidder").asInt())
                        .collect(Collectors.toList()))
                .containsOnly(asList(1, 3), singletonList(2));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPassImpWithExtPrebidToDefinedBidder() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";

        final ObjectNode impExt = mapper.createObjectNode()
                .put(bidder1Name, "ignored1")
                .put(bidder2Name, "ignored2")
                .putPOJO("prebid", doubleMap(bidder1Name, mapper.createObjectNode().put("someField", "bidder1"),
                        bidder2Name, mapper.createObjectNode().put("someField", "bidder2")));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(impExt, identity())), identity());

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ArgumentCaptor<List<AuctionParticipation>> auctionParticipationsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(bidRequester).waitForBidResponses(auctionParticipationsCaptor.capture(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any());

        final ObjectNode firsImpExtPrebid = mapper.createObjectNode()
                .set("bidder", mapper.createObjectNode().put("someField", "bidder1"));
        final JsonNode secondImpExtPrebid = mapper.createObjectNode().set("bidder",
                mapper.createObjectNode().put("someField", "bidder2"));
        assertThat(auctionParticipationsCaptor.getValue())
                .extracting(AuctionParticipation::getBidderRequest)
                .flatExtracting(bidderRequest -> bidderRequest.getBidRequest().getImp())
                .extracting(imp -> imp.getExt().get("prebid"))
                .containsOnly(firsImpExtPrebid, secondImpExtPrebid);
    }

    @Test
    public void shouldPassRequestWithExtPrebidToDefinedBidder() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";

        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .bidders(mapper.createObjectNode()
                                .putPOJO(bidder1Name, mapper.createObjectNode().put("test1", "test1"))
                                .putPOJO(bidder2Name, mapper.createObjectNode().put("test2", "test2"))
                                .putPOJO("spam", mapper.createObjectNode().put("spam", "spam")))
                        .auctiontimestamp(1000L)
                        .build());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap(bidder1Name, 1), identity()),
                givenImp(singletonMap(bidder2Name, 2), identity())),
                builder -> builder.ext(extRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(request -> request.getExt().getPrebid().getBidders())
                .containsOnly(
                        mapper.createObjectNode().set("bidder", mapper.createObjectNode().put("test1", "test1")),
                        mapper.createObjectNode().set("bidder", mapper.createObjectNode().put("test2", "test2")));
    }

    @Test
    public void shouldPassRequestWithInjectedSchainInSourceExt() {
        // given
        final String bidder1Name = "bidder1";
        final String bidder2Name = "bidder2";
        final String bidder3Name = "bidder3";

        final ObjectNode schainExtObjectNode = mapper.createObjectNode().put("any", "any");
        final ExtRequestPrebidSchainSchainNode specificNodes = ExtRequestPrebidSchainSchainNode.of("asi", "sid", 1,
                "rid", "name", "domain", schainExtObjectNode);
        final ExtRequestPrebidSchainSchain specificSchain = ExtRequestPrebidSchainSchain.of("ver", 1,
                singletonList(specificNodes), schainExtObjectNode);
        final ExtRequestPrebidSchain schainForBidders = ExtRequestPrebidSchain.of(
                asList(bidder1Name, bidder2Name),
                specificSchain);
        final ExtRequestPrebidSchainSchainNode generalNodes = ExtRequestPrebidSchainSchainNode.of("t", null, 0, "a",
                null, "ads", null);
        final ExtRequestPrebidSchainSchain generalSchain = ExtRequestPrebidSchainSchain.of("t", 123,
                singletonList(generalNodes), null);
        final ExtRequestPrebidSchain allSchain = ExtRequestPrebidSchain.of(singletonList("*"), generalSchain);
        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .schains(asList(schainForBidders, allSchain))
                        .auctiontimestamp(1000L)
                        .build());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap(bidder1Name, 1), identity()),
                givenImp(singletonMap(bidder2Name, 2), identity()),
                givenImp(singletonMap(bidder3Name, 3), identity())),
                builder -> builder.ext(extRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(3)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(request -> request.getSource().getExt().getSchain())
                .containsOnly(specificSchain, specificSchain, generalSchain);

        assertThat(auctionParticipations).hasSize(3)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(request -> request.getExt().getPrebid().getSchains())
                .containsOnly(null, null, null);
    }

    @Test
    public void shouldRejectDuplicatedSchainBidders() {
        // given
        final String bidder1 = "bidder";
        final String bidder2 = "bidder"; // same name

        final ExtRequestPrebidSchain schainForBidder1 = ExtRequestPrebidSchain.of(
                singletonList(bidder1), ExtRequestPrebidSchainSchain.of("ver1", null, null, null));
        final ExtRequestPrebidSchain schainForBidder2 = ExtRequestPrebidSchain.of(
                singletonList(bidder2), ExtRequestPrebidSchainSchain.of("ver2", null, null, null));

        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .schains(asList(schainForBidder1, schainForBidder2))
                        .build());

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(singletonMap(bidder1, 1), identity()),
                givenImp(singletonMap(bidder2, 2), identity())),
                builder -> builder.ext(extRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getSource)
                .containsNull();
    }

    @Test
    public void shouldReturnFailedFutureWithUnchangedMessageWhenPrivacyEnforcementServiceFails() {
        // given
        given(privacyEnforcementService.mask(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("Error when retrieving allowed purpose ids"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder
                        .regs(Regs.of(null, ExtRegs.of(1, null))));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("Error when retrieving allowed purpose ids");
    }

    @Test
    public void shouldNotCreateRequestForBidderRestrictedByPrivacyEnforcement() {
        // given
        final BidderPrivacyResult restrictedPrivacy = BidderPrivacyResult.builder()
                .requestBidder("bidderAlias")
                .blockedRequestByTcf(true)
                .build();
        given(privacyEnforcementService.mask(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(singletonList(restrictedPrivacy)));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .build())));

        // when
        final Future<AuctionContext> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.result().getAuctionParticipations())
                .containsOnly(AuctionParticipation.builder().bidder("bidderAlias").requestBlocked(true).build());
    }

    @Test
    public void shouldExtractRequestByAliasForCorrectBidder() {
        // given
        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(singletonMap("bidderAlias", 1), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(1);
    }

    @Test
    public void shouldExtractMultipleRequestsForTheSameBidderIfAliasesWereUsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("bidder", 1, "bidderAlias", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidderAlias", "bidder"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("bidder").asInt())
                .containsOnly(2, 1);

    }

    @Test
    public void shouldTolerateBidderResultWithoutBids() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));
        givenWaitForBidResponses(givenEmptyBidderResponse("bidder"));

        givenBidResponseCreator(emptyMap());

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCallBidResponseCreatorWithExpectedParams() {
        // given
        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenWaitForBidResponses(
                givenEmptyBidderResponse("bidder1"),
                givenBidderResponse("bidder2", givenSeatBid(singletonList(givenBid(thirdBid)))));

        final ExtRequestTargeting targeting = givenTargeting(true);
        final ObjectNode events = mapper.createObjectNode();
        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .auctiontimestamp(1000L)
                        .events(events)
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(53, true),
                                ExtRequestPrebidCacheVastxml.of(34, true), true))
                        .build())));
        final AuctionContext auctionContext = givenRequestContext(bidRequest);

        // when
        exchangeService.holdAuction(auctionContext).result();

        // then
        final BidRequestCacheInfo expectedCacheInfo = BidRequestCacheInfo.builder()
                .doCaching(true)
                .shouldCacheBids(true)
                .shouldCacheVideoBids(true)
                .returnCreativeBids(true)
                .returnCreativeVideoBids(true)
                .cacheBidsTtl(53)
                .cacheVideoBidsTtl(34)
                .shouldCacheWinningBidsOnly(false)
                .build();

        final ArgumentCaptor<List<AuctionParticipation>> captor = ArgumentCaptor.forClass(List.class);
        verify(bidResponseCreator).create(captor.capture(), eq(auctionContext), eq(expectedCacheInfo), eq(false));

        assertThat(captor.getValue())
                .extracting(AuctionParticipation::getBidderResponse)
                .containsOnly(
                        BidderResponse.of("bidder2", BidderSeatBid.of(singletonList(
                                BidderBid.of(thirdBid, banner, null)), emptyList(), emptyList()), 0),
                        BidderResponse.of("bidder1", BidderSeatBid.of(emptyList(), emptyList(), emptyList()), 0));
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyTrueWhenIncludeBidderKeysIsFalse() {
        // given
        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenWaitForBidResponses(
                givenEmptyBidderResponse("bidder1"),
                givenBidderResponse("bidder2", givenSeatBid(singletonList(givenBid(thirdBid)))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, true))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final ArgumentCaptor<AuctionContext> auctionContextArgumentCaptor =
                ArgumentCaptor.forClass(AuctionContext.class);
        verify(bidResponseCreator).create(
                anyList(),
                auctionContextArgumentCaptor.capture(),
                eq(BidRequestCacheInfo.builder().doCaching(true).shouldCacheWinningBidsOnly(true).build()),
                eq(false));

        assertThat(singletonList(auctionContextArgumentCaptor.getValue().getBidRequest()))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldCallBidResponseCreatorWithWinningOnlyFalseWhenWinningOnlyIsNull() {
        // given
        final Bid thirdBid = Bid.builder().id("bidId3").impid("impId1").price(BigDecimal.valueOf(7.89)).build();
        givenWaitForBidResponses(
                givenEmptyBidderResponse("bidder1"),
                givenBidderResponse("bidder2", givenSeatBid(singletonList(givenBid(thirdBid)))));

        final ExtRequestTargeting targeting = givenTargeting(false);

        final BidRequest bidRequest = givenBidRequest(asList(
                // imp ids are not really used for matching, included them here for clarity
                givenImp(singletonMap("bidder1", 1), builder -> builder.id("impId1")),
                givenImp(doubleMap("bidder1", 1, "bidder2", 2), builder -> builder.id("impId1"))),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(targeting)
                        .cache(ExtRequestPrebidCache.of(null, null, null))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(
                anyList(),
                any(),
                eq(BidRequestCacheInfo.builder().build()),
                anyBoolean());
    }

    @Test
    public void shouldCallBidResponseCreatorWithEnabledDebugTrueIfTestFlagIsTrue() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList());
        givenWaitForBidResponses(givenBidderResponse("bidder1", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidder1", 1)),
                builder -> builder.test(1));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(anyList(), any(), any(), eq(true));
    }

    @Test
    public void shouldCallBidResponseCreatorWithEnabledDebugTrueIfExtPrebidDebugIsOn() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(BigDecimal.ONE).build())),
                singletonList(ExtHttpCall.builder()
                        .uri("bidder1_uri1")
                        .requestbody("bidder1_requestBody1")
                        .status(200)
                        .responsebody("bidder1_responseBody1")
                        .build()),
                emptyList());
        givenWaitForBidResponses(givenBidderResponse("bidder1", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("bidder1", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        verify(bidResponseCreator).create(anyList(), any(), any(), eq(true));
    }

    @Test
    public void shouldTolerateNullRequestExtPrebid() {
        // given
        final BidderSeatBid bidderSeatBid = givenSingleSeatBid(givenBid(Bid.builder().price(BigDecimal.ONE).build()));
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(jacksonMapper.fillExtension(ExtRequest.empty(), singletonMap("someField", 1))));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldTolerateNullRequestExtPrebidTargeting() {
        // given
        final BidderSeatBid bidderSeatBid = givenSingleSeatBid(givenBid(Bid.builder().price(BigDecimal.ONE).build()));
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(
                givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getTargeting())
                .allSatisfy(map -> assertThat(map).isNull());
    }

    @Test
    public void shouldCreateRequestsFromImpsReturnedByStoredResponseProcessor() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder1"), givenEmptyBidderResponse("someBidder2"));

        final BidRequest bidRequest = givenBidRequest(asList(
                givenImp(doubleMap("prebid", 0, "someBidder1", 1), builder -> builder
                        .id("impId1")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build())),
                givenImp(doubleMap("prebid", 0, "someBidder2", 1), builder -> builder
                        .id("impId2")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseResult
                        .of(singletonList(givenImp(doubleMap("prebid", 0, "someBidder1", 1), builder -> builder
                                .id("impId1")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build()))), emptyList())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .containsOnly(BidRequest.builder()
                        .id("requestId")
                        .cur(singletonList("USD"))
                        .imp(singletonList(Imp.builder()
                                .id("impId1")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(400).h(300).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(0, 1)))
                                .build()))
                        .tmax(500L)
                        .build());
    }

    @Test
    public void shouldProcessBidderResponseReturnedFromStoredResponseProcessor() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder1"));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        final Bid bidId1 = Bid.builder().id("bidId1").price(ONE).build();
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(
                singletonList(BidderBid.of(bidId1, banner, "USD")),
                null,
                emptyList());
        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willReturn(singletonList(AuctionParticipation.builder()
                        .bidder("someBidder")
                        .bidderResponse(BidderResponse.of("someBidder", bidderSeatBid, 100))
                        .build()));

        givenBidResponseCreator(singletonList(bidId1));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getId)
                .containsOnly("bidId1");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredResponseProcessorGetStoredResultReturnsFailedFuture() {
        // given
        given(storedResponseProcessor.getStoredResponseResult(any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Error")));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class).hasMessage("Error");
    }

    @Test
    public void shouldReturnFailedFutureWhenStoredResponseProcessorMergeBidderResponseReturnsFailedFuture() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        given(storedResponseProcessor.mergeWithBidderResponses(any(), any(), any()))
                .willThrow(new PreBidException("Error"));

        final BidRequest bidRequest = givenBidRequest(singletonList(
                givenImp(doubleMap("prebid", 0, "someBidder", 1), builder -> builder
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()))),
                builder -> builder.id("requestId").tmax(500L));

        // when
        final Future<?> result = exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class).hasMessage("Error");
    }

    @Test
    public void shouldNotModifyUserFromRequestIfNoBuyeridInCookie() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        // this is not required but stated for clarity's sake. The case when bidder is disabled.
        given(bidderCatalog.isActive(anyString())).willReturn(false);
        given(uidsCookie.uidFrom(any())).willReturn(null);

        final User user = User.builder().id("userId").build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(user));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(uidsCookie).uidFrom(isNull());

        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(user);
    }

    @Test
    public void shouldHonorBuyeridFromRequestAndClearBuyerIdsFromUserExtPrebidIfContains() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder()
                        .buyeruid("buyeridFromRequest")
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("someBidder", "uidval")))
                                .build())
                        .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder()
                        .buyeruid("buyeridFromRequest")
                        .build());
    }

    @Test
    public void shouldNotChangeGdprFromRequestWhenDeviceLmtIsOne() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final Regs regs = Regs.of(null, null);
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().build())
                        .device(Device.builder().lmt(1).build())
                        .regs(regs));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getRegs)
                .containsOnly(regs);
    }

    @Test
    public void shouldCleanImpExtContextDataWhenFirstPartyDataNotPermittedForBidder() {
        // given
        final ObjectNode impExt = mapper.createObjectNode().put("someBidder", 1).set("context",
                mapper.createObjectNode().put("data", "data").put("otherField", "value"));
        final BidRequest bidRequest = givenBidRequest(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()).ext(impExt).build()),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("otherBidder")))
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .flatExtracting(BidRequest::getImp)
                .extracting(imp -> imp.getExt().get("context"))
                .containsOnly(mapper.createObjectNode().put("otherField", "value"));
    }

    @Test
    public void shouldDeepCopyImpExtContextToEachImpressionAndNotRemoveDataForAllWhenDeprecatedOnlyOneBidder() {
        // given
        final ObjectNode impExt = mapper.createObjectNode().put("someBidder", 1).put("deprecatedBidder", 2)
                .set("context", mapper.createObjectNode().put("data", "data").put("otherField", "value"));
        final BidRequest bidRequest = givenBidRequest(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(400).h(300).build()))
                                .build()).ext(impExt).build()),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(impExtNode -> impExtNode.get("context"))
                .containsOnly(
                        // data erased for deprecatedBidder
                        mapper.createObjectNode().put("otherField", "value"),
                        // data present for someBidder
                        mapper.createObjectNode().put("data", "data").put("otherField", "value"));
    }

    @Test
    public void shouldSetUserBuyerIdsFromUserExtPrebidAndClearPrebidBuyerIdsAfterwards() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        given(uidsCookie.uidFrom(anyString())).willReturn("buyeridFromCookie");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .prebid(ExtUserPrebid.of(singletonMap("someBidder", "uidval")))
                                        .build())
                                .build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                                .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder()
                        .buyeruid("uidval")
                        .build());
    }

    @Test
    public void shouldCleanRequestExtPrebidData() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(asList("someBidder", "should_be_removed")))
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .auctiontimestamp(1000L)
                        .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getExt)
                .containsOnly(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("someBidder", "alias_should_stay"))
                        .auctiontimestamp(1000L)
                        .build()));
    }

    @Test
    public void shouldPassUserExtDataOnlyForAllowedBidder() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final ExtUserDigiTrust extUserDigiTrust = ExtUserDigiTrust.of("dId", 23, 222);
        final List<ExtUserEid> eids = singletonList(ExtUserEid.of("eId", "id", emptyList(), null));
        final ExtUser extUser = ExtUser.builder().data(dataNode).digitrust(extUserDigiTrust).eids(eids).build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                                .build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .ext(extUser)
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtUser maskedExtUser = ExtUser.builder().digitrust(extUserDigiTrust).eids(eids).build();

        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords, User::getGender, User::getYob, User::getGeo, User::getExt)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY, extUser),
                        tuple("keyword", "male", 133, Geo.EMPTY, maskedExtUser));
    }

    @Test
    public void shouldNotCleanRequestExtPrebidDataWhenFpdAllowedAndPrebidIsNotNull() {
        // given
        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = singletonMap("someBidder", 1);
        final ExtUser extUser = ExtUser.builder().prebid(ExtUserPrebid.of(emptyMap())).data(dataNode).build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .auctiontimestamp(1000L)
                                .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                                .build()))
                        .user(User.builder()
                                .ext(extUser)
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(ExtUser.builder().data(dataNode).build());
    }

    @Test
    public void shouldMaskUserExtIfDataBiddersListIsEmpty() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);
        final ExtUserDigiTrust extUserDigiTrust = ExtUserDigiTrust.of("dId", 23, 222);
        final List<ExtUserEid> eids = singletonList(ExtUserEid.of("eId", "id", emptyList(), null));
        final ExtUser extUser = ExtUser.builder().data(dataNode).digitrust(extUserDigiTrust).eids(eids).build();

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(emptyList())).build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .ext(extUser)
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final ExtUser expectedExtUser = ExtUser.builder().digitrust(extUserDigiTrust).eids(eids).build();
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords, User::getGender, User::getYob, User::getGeo, User::getExt)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY, expectedExtUser),
                        tuple("keyword", "male", 133, Geo.EMPTY, expectedExtUser));
    }

    @Test
    public void shouldNoMaskUserExtIfDataBiddersListIsNull() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(null)).build()))
                        .user(User.builder()
                                .keywords("keyword")
                                .gender("male")
                                .yob(133)
                                .geo(Geo.EMPTY)
                                .ext(ExtUser.builder().data(dataNode).build())
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .extracting(User::getKeywords, User::getGender, User::getYob, User::getGeo, User::getExt)
                .containsOnly(
                        tuple("keyword", "male", 133, Geo.EMPTY, ExtUser.builder().data(dataNode).build()),
                        tuple("keyword", "male", 133, Geo.EMPTY, ExtUser.builder().data(dataNode).build()));
    }

    @Test
    public void shouldPassSiteExtDataOnlyForAllowedBidder() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .auctiontimestamp(1000L)
                        .data(ExtRequestPrebidData.of(singletonList("someBidder"))).build()))
                        .site(Site.builder()
                                .keywords("keyword")
                                .search("search")
                                .ext(ExtSite.of(0, dataNode))
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords, Site::getSearch, Site::getExt)
                .containsOnly(
                        tuple("keyword", "search", ExtSite.of(0, dataNode)),
                        tuple("keyword", "search", ExtSite.of(0, null)));
    }

    @Test
    public void shouldNoMaskPassAppExtAndKeywordsWhenDataBiddersListIsNull() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null)).build()))
                        .app(App.builder()
                                .keywords("keyword")
                                .ext(ExtApp.of(null, dataNode))
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getApp)
                .extracting(App::getExt, App::getKeywords)
                .containsOnly(
                        tuple(ExtApp.of(null, dataNode), "keyword"),
                        tuple(ExtApp.of(null, dataNode), "keyword"));
    }

    @Test
    public void shouldPassAppExtDataOnlyForAllowedBidder() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"), givenEmptyBidderResponse("missingBidder"));

        final ObjectNode dataNode = mapper.createObjectNode().put("data", "value");
        final Map<String, Integer> bidderToGdpr = doubleMap("someBidder", 1, "missingBidder", 0);

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(bidderToGdpr),
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .data(ExtRequestPrebidData.of(singletonList("someBidder")))
                                .auctiontimestamp(1000L)
                                .build()))
                        .app(App.builder()
                                .keywords("keyword")
                                .ext(ExtApp.of(null, dataNode))
                                .build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(2)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getApp)
                .extracting(App::getExt, App::getKeywords)
                .containsOnly(
                        tuple(ExtApp.of(null, dataNode), "keyword"),
                        tuple(null, "keyword"));
    }

    @Test
    public void shouldRejectRequestWhenAppAndSiteAppearsTogetherAfterFpdMerge() {
        // given
        final ObjectNode bidderConfigApp = mapper.valueToTree(App.builder().id("appFromConfig").build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(null, bidderConfigApp, null));
        final ExtRequestPrebidBidderConfig extRequestPrebidBidderConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("someBidder"), extBidderConfig);
        final Site requestSite = Site.builder().id("erased").domain("domain").build();
        final ExtRequest extRequest = ExtRequest.of(
                ExtRequestPrebid.builder()
                        .bidderconfig(singletonList(extRequestPrebidBidderConfig))
                        .build());
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(requestSite).ext(extRequest));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verifyZeroInteractions(bidRequester);
    }

    @Test
    public void shouldUseConcreteOverGeneralSiteWithExtPrebidBidderConfig() {
        // given
        final ObjectNode siteWithPage = mapper.valueToTree(Site.builder().page("testPage").build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(siteWithPage, null, null));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("someBidder"), extBidderConfig);
        final ObjectNode siteWithDomain = mapper.valueToTree(Site.builder().domain("notUsed").build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(siteWithDomain, null, null));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);

        final Site requestSite = Site.builder().id("siteId").page("erased").keywords("keyword").build();
        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.site(requestSite).ext(ExtRequest.of(extRequestPrebid)));

        final Site mergedSite = Site.builder()
                .id("siteId")
                .page("testPage")
                .keywords("keyword")
                .build();

        given(fpdResolver.resolveSite(any(), any())).willReturn(mergedSite);

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getSite)
                .containsOnly(mergedSite);
    }

    @Test
    public void shouldUseConcreteOverGeneralAppWithExtPrebidBidderConfig() {
        // given
        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final ObjectNode appWithPublisherId = mapper.valueToTree(App.builder().publisher(publisherWithId).build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(null, appWithPublisherId, null));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("someBidder"), extBidderConfig);

        final Publisher publisherWithIdAndDomain = Publisher.builder().id("notUsed").domain("notUsed").build();
        final ObjectNode appWithUpdatedPublisher = mapper.valueToTree(
                App.builder().publisher(publisherWithIdAndDomain).build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(null, appWithUpdatedPublisher, null));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);

        final App requestApp = App.builder().publisher(Publisher.builder().build()).build();

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.app(requestApp).ext(ExtRequest.of(extRequestPrebid)));
        final App mergedApp = App.builder()
                .publisher(Publisher.builder().id("testId").build())
                .build();

        given(fpdResolver.resolveApp(any(), any())).willReturn(mergedApp);

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getApp)
                .containsOnly(mergedApp);
    }

    @Test
    public void shouldUseConcreteOverGeneralUserWithExtPrebidBidderConfig() {
        // given
        final ObjectNode bidderConfigUser = mapper.valueToTree(User.builder().id("userFromConfig").build());
        final ExtBidderConfig extBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(null, null, bidderConfigUser));
        final ExtRequestPrebidBidderConfig concreteFpdConfig = ExtRequestPrebidBidderConfig.of(
                singletonList("someBidder"), extBidderConfig);

        final ObjectNode emptyUser = mapper.valueToTree(User.builder().build());
        final ExtBidderConfig allExtBidderConfig = ExtBidderConfig.of(
                ExtBidderConfigFpd.of(null, null, emptyUser));
        final ExtRequestPrebidBidderConfig allFpdConfig = ExtRequestPrebidBidderConfig.of(singletonList("*"),
                allExtBidderConfig);
        final User requestUser = User.builder().id("erased").buyeruid("testBuyerId").build();

        final ExtRequestPrebid extRequestPrebid = ExtRequestPrebid.builder()
                .bidderconfig(asList(allFpdConfig, concreteFpdConfig))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(requestUser).ext(ExtRequest.of(extRequestPrebid)));

        final User mergedUser = User.builder().id("userFromConfig").buyeruid("testBuyerId").build();

        given(fpdResolver.resolveUser(any(), any())).willReturn(mergedUser);

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(mergedUser);
    }

    @Test
    public void shouldAddBuyeridToUserFromRequest() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));
        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                builder -> builder.user(User.builder().id("userId").build()));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder().id("userId").buyeruid("buyerid").build());
    }

    @Test
    public void shouldCreateUserIfMissingInRequestAndBuyeridPresentInCookie() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        given(uidsCookie.uidFrom(eq("cookieFamily"))).willReturn("buyerid");

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        final List<AuctionParticipation> auctionParticipations = captureAuctionParticipansWithBidRequest();
        assertThat(auctionParticipations).hasSize(1)
                .extracting(participation -> participation.getBidderRequest().getBidRequest())
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder().buyeruid("buyerid").build());
    }

    @Test
    public void shouldNotAddExtPrebidEventsWhenEventsServiceReturnsEmptyEventsService() {
        // given
        final BigDecimal price = BigDecimal.valueOf(2.0);
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(
                singletonList(BidderBid.of(
                        Bid.builder().id("bidId").price(price)
                                .ext(mapper.valueToTree(singletonMap("bidExt", 1))).build(), banner, null)),
                emptyList(),
                emptyList());
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)),
                bidRequestBuilder -> bidRequestBuilder.app(App.builder()
                        .publisher(Publisher.builder().id("1001").build()).build()));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid()).hasSize(1)
                .flatExtracting(SeatBid::getBid)
                .extracting(bid -> toExtPrebid(bid.getExt()).getPrebid().getEvents())
                .containsNull();
    }

    @Test
    public void shouldIncrementCommonMetrics() {
        // given
        final BidderSeatBid bidderSeatBid = givenSeatBid(singletonList(givenBid(Bid.builder().price(TEN).build())));
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someAlias", 1)),
                builder -> builder
                        .site(Site.builder().publisher(Publisher.builder().id("accountId").build()).build())
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .aliases(singletonMap("someAlias", "someBidder"))
                                .build())));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAccountRequestMetrics(eq("accountId"), eq(MetricName.openrtb2web));
        verify(metrics)
                .updateAdapterRequestTypeAndNoCookieMetrics(eq("someBidder"), eq(MetricName.openrtb2web), eq(true));
        verify(metrics).updateAdapterResponseTime(eq("someBidder"), eq("accountId"), anyInt());
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), eq("accountId"));
        verify(metrics).updateAdapterBidMetrics(eq("someBidder"), eq("accountId"), eq(10000L), eq(false), eq("banner"));
    }

    @Test
    public void shouldCallUpdateCookieMetricsWithExpectedValue() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someAlias", 1)),
                builder -> builder.app(App.builder().build()));

        given(bidderCatalog.nameByAlias("someAlias")).willReturn("someBidder");

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestTypeAndNoCookieMetrics(
                eq("someBidder"), eq(MetricName.openrtb2web), eq(false));
    }

    @Test
    public void shouldUseEmptyStringIfPublisherIdIsEmpty() {
        // given
        final BidderSeatBid bidderSeatBid = givenSeatBid(singletonList(givenBid(Bid.builder().price(TEN).build())));
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));
        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));
        final Account account = Account.builder().id("").build();

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest, account));

        // then
        verify(metrics).updateAccountRequestMetrics(eq(""), eq(MetricName.openrtb2web));
    }

    @Test
    public void shouldIncrementNoBidRequestsMetric() {
        // given
        givenWaitForBidResponses(givenEmptyBidderResponse("someBidder"));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestNobidMetrics(eq("someBidder"), eq("accountId"));
    }

    @Test
    public void shouldIncrementGotBidsAndErrorMetricsIfBidderReturnsBidAndDifferentErrors() {
        // given
        final BidderSeatBid bidderSeatBid = BidderSeatBid.of(
                singletonList(givenBid(Bid.builder().price(TEN).build())),
                emptyList(),
                asList(
                        // two identical errors to verify corresponding metric is submitted only once
                        BidderError.badInput("rubicon error"),
                        BidderError.badInput("rubicon error"),
                        BidderError.badServerResponse("rubicon error"),
                        BidderError.failedToRequestBids("rubicon failed to request bids"),
                        BidderError.timeout("timeout error"),
                        BidderError.generic("timeout error")));
        givenWaitForBidResponses(givenBidderResponse("someBidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(givenSingleImp(singletonMap("someBidder", 1)));

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(metrics).updateAdapterRequestGotbidsMetrics(eq("someBidder"), eq("accountId"));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.badinput));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.badserverresponse));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.failedtorequestbids));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.timeout));
        verify(metrics).updateAdapterRequestErrorMetric(eq("someBidder"), eq(MetricName.unknown_error));
    }

    @Test
    public void shouldPassResponseToPostProcessor() {
        // given
        final BidRequest bidRequest = givenBidRequest(emptyList());

        // when
        exchangeService.holdAuction(givenRequestContext(bidRequest));

        // then
        verify(bidResponsePostProcessor).postProcess(any(), same(uidsCookie), same(bidRequest), any(),
                eq(Account.builder().id("accountId").eventsEnabled(true).build()));
    }

    @Test
    public void shouldReturnBidsWithAdjustedPricesWhenAdjustmentFactorPresent() {
        // given
        final BidderSeatBid bidderSeatBid = givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.valueOf(2)).build())));
        givenWaitForBidResponses(givenBidderResponse("bidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .bidadjustmentfactors(singletonMap("bidder", BigDecimal.valueOf(2.468)))
                        .auctiontimestamp(1000L)
                        .build())));

        givenBidResponseCreator(singletonList(Bid.builder().price(BigDecimal.valueOf(4.936)).build()));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.valueOf(4.936));
    }

    @Test
    public void shouldReturnBidsWithoutAdjustingPricesWhenAdjustmentFactorNotPresentForBidder() {
        // given

        final BidderSeatBid bidderSeatBid = givenSeatBid(singletonList(
                givenBid(Bid.builder().price(BigDecimal.ONE).build())));
        givenWaitForBidResponses(givenBidderResponse("bidder", bidderSeatBid));

        final BidRequest bidRequest = givenBidRequest(singletonList(givenImp(singletonMap("bidder", 2), identity())),
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(emptyMap())
                        .auctiontimestamp(1000L)
                        .currency(ExtRequestCurrency.of(null, false))
                        .bidadjustmentfactors(singletonMap("some-other-bidder", BigDecimal.TEN))
                        .build())));

        // when
        final AuctionContext result = exchangeService.holdAuction(givenRequestContext(bidRequest)).result();

        // then
        assertThat(result.getBidResponse().getSeatbid())
                .flatExtracting(SeatBid::getBid)
                .extracting(Bid::getPrice)
                .containsExactly(BigDecimal.ONE);
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest) {
        return givenRequestContext(bidRequest, Account.builder().id("accountId").eventsEnabled(true).build());
    }

    private AuctionContext givenRequestContext(BidRequest bidRequest, Account account) {
        return AuctionContext.builder()
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .account(account)
                .requestTypeMetric(MetricName.openrtb2web)
                .timeout(timeout)
                .build();
    }

    private List<AuctionParticipation> captureAuctionParticipansWithBidRequest() {
        @SuppressWarnings("unchecked") final ArgumentCaptor<List<AuctionParticipation>> auctionParticipationsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(bidRequester).waitForBidResponses(auctionParticipationsCaptor.capture(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), any());
        return auctionParticipationsCaptor.getValue();
    }

    private static BidRequest givenBidRequest(
            List<Imp> imp, Function<BidRequestBuilder, BidRequestBuilder> bidRequestBuilderCustomizer) {
        return bidRequestBuilderCustomizer.apply(BidRequest.builder().cur(singletonList("USD")).imp(imp)).build();
    }

    private static BidRequest givenBidRequest(List<Imp> imp) {
        return givenBidRequest(imp, identity());
    }

    private static <T> Imp givenImp(T ext, Function<ImpBuilder, ImpBuilder> impBuilderCustomizer) {
        return impBuilderCustomizer.apply(Imp.builder()
                .ext(ext != null ? mapper.valueToTree(ext) : null))
                .build();
    }

    private static <T> List<Imp> givenSingleImp(T ext) {
        return singletonList(givenImp(ext, identity()));
    }

    private static SeatBid givenSeatBid(List<Bid> bids,
                                        Function<SeatBid.SeatBidBuilder, SeatBid.SeatBidBuilder> seatBidCustomizer) {
        return seatBidCustomizer.apply(SeatBid.builder()
                .seat("someBidder")
                .bid(bids))
                .build();
    }

    private static BidderSeatBid givenSeatBid(List<BidderBid> bids) {
        return BidderSeatBid.of(bids, emptyList(), emptyList());
    }

    private static BidderSeatBid givenSingleSeatBid(BidderBid bid) {
        return givenSeatBid(singletonList(bid));
    }

    private static BidderResponse givenEmptyBidderResponse(String bidder) {
        return givenBidderResponse(bidder, givenSeatBid(emptyList()));
    }

    private static BidderResponse givenBidderResponse(String bidder, BidderSeatBid bidderSeatBid) {
        return BidderResponse.of(bidder, bidderSeatBid, 0);
    }

    private static BidderBid givenBid(Bid bid) {
        return BidderBid.of(bid, banner, null);
    }

    private static Bid givenBid(Function<Bid.BidBuilder, Bid.BidBuilder> bidBuilder) {
        return bidBuilder.apply(Bid.builder()
                .id("bidId")
                .price(BigDecimal.ONE)
                .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().build(), null))))
                .build();
    }

    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static ExtPrebid<ExtBidPrebid, ?> toExtPrebid(ObjectNode ext) {
        try {
            return mapper.readValue(mapper.treeAsTokens(ext), new TypeReference<ExtPrebid<ExtBidPrebid, ?>>() {
            });
        } catch (IOException e) {
            return rethrow(e);
        }
    }

    private static ExtRequestTargeting givenTargeting(boolean includebidderkeys) {
        return ExtRequestTargeting.builder().pricegranularity(mapper.valueToTree(
                ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                        BigDecimal.valueOf(0.5))))))
                .includewinners(true)
                .includebidderkeys(includebidderkeys)
                .build();
    }

    private void givenWaitForBidResponses(BidderResponse... bidderResponse) {
        given(bidRequester.waitForBidResponses(any(), any(), anyBoolean(), anyBoolean(), any(), any(), any(), any()))
                .willAnswer(inv -> {
                    final List<AuctionParticipation> auctionParticipations = inv.getArgument(0);
                    for (int i = 0; i < bidderResponse.length; i++) {
                        auctionParticipations.get(i).insertBidderResponse(bidderResponse[i]);
                    }
                    return Future.succeededFuture(auctionParticipations);
                });
    }

    private void givenBidResponseCreator(List<Bid> bids) {
        given(bidResponseCreator.create(anyList(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenBidResponseWithBids(bids)));
    }

    private void givenBidResponseCreator(Map<String, List<ExtBidderError>> errors) {
        given(bidResponseCreator.create(anyList(), any(), any(), anyBoolean()))
                .willReturn(Future.succeededFuture(givenBidResponseWithError(errors)));
    }

    private static BidResponse givenBidResponseWithBids(List<Bid> bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(givenSeatBid(bids, identity())))
                .build();
    }

    private static BidResponse givenBidResponseWithError(Map<String, List<ExtBidderError>> errors) {
        return BidResponse.builder()
                .seatbid(emptyList())
                .ext(mapper.valueToTree(ExtBidResponse.of(null, errors, null, null, null, null)))
                .build();
    }
}
