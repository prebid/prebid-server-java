package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodesBidder;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class BidderRequestCleanerTest extends VertxTest {

    private static final String BIDDER = "bidder";

    private BidderRequestCleaner target;

    @BeforeEach
    public void setUp() {
        target = new BidderRequestCleaner();
    }

    @Test
    public void processShouldReturnSameRequest() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(identity());

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue()).isSameAs(bidderRequest);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldCleanBidAdjustmentFactors() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> mediaTypes = new EnumMap<>(ImpMediaType.class);
        mediaTypes.put(ImpMediaType.banner, Map.of("other", BigDecimal.ONE));
        mediaTypes.put(ImpMediaType.video, Map.of(
                "other", BigDecimal.ONE,
                "biddEr", BigDecimal.ONE));
        final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(mediaTypes)
                .build();
        factors.addFactor("bIdder", BigDecimal.ONE);

        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.bidadjustmentfactors(factors));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustmentfactors)
                .satisfies(factorsResult -> {
                    assertThat(factorsResult.getAdjustments()).containsExactly(Map.entry("bIdder", BigDecimal.ONE));
                    assertThat(factorsResult.getMediatypes()).containsExactly(
                            Map.entry(ImpMediaType.video, Map.of("biddEr", BigDecimal.ONE)));
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveBidAdjustmentFactors() {
        // given
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> mediaTypes = new EnumMap<>(ImpMediaType.class);
        mediaTypes.put(ImpMediaType.banner, Map.of("other", BigDecimal.ONE));
        final ExtRequestBidAdjustmentFactors factors = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(mediaTypes)
                .build();
        factors.addFactor("other", BigDecimal.ONE);

        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.bidadjustmentfactors(factors));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustmentfactors)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldCleanBidAdjustments() {
        // given
        final ObjectNode bidAdjustments = mapper.valueToTree(Map.of(
                "mediatype", Map.of(
                        "banner", Map.of("other", 1),
                        "video", Map.of("other", 1, "biddEr", 1))));

        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.bidadjustments(bidAdjustments));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isEqualTo(mapper.valueToTree(Map.of("mediatype", Map.of("video", Map.of("biddEr", 1)))));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveBidAdjustments() {
        // given
        final ObjectNode bidAdjustments = mapper.valueToTree(Map.of(
                "mediatype", Map.of("banner", Map.of("other", 1))));

        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.bidadjustments(bidAdjustments));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBidadjustments)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldCleanAlternateBidderCodes() {
        // given
        final ExtRequestPrebidAlternateBidderCodes codes = ExtRequestPrebidAlternateBidderCodes.of(
                true, Map.of(
                        "other", ExtRequestPrebidAlternateBidderCodesBidder.of(true, singleton("otherV")),
                        "biddEr", ExtRequestPrebidAlternateBidderCodesBidder.of(true, singleton("bidderV"))));

        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.alternateBidderCodes(codes));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAlternateBidderCodes)
                .isEqualTo(ExtRequestPrebidAlternateBidderCodes.of(true, Map.of(
                        "biddEr", ExtRequestPrebidAlternateBidderCodesBidder.of(true, singleton("bidderV")))));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveReturnAllBidStatus() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.returnallbidstatus(true));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getReturnallbidstatus)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveAliasGvlIds() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.aliasgvlids(emptyMap()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAliasgvlids)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveAdServerTargeting() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.adservertargeting(emptyList()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAdservertargeting)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveCache() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.cache(ExtRequestPrebidCache.EMPTY));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveEvents() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.events(mapper.createObjectNode()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getEvents)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveNoSale() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(extPrebid -> extPrebid.nosale(emptyList()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getNosale)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveBidderControls() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.biddercontrols(mapper.createObjectNode()));

        // when
        final BidderRequestPostProcessingResult result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getBiddercontrols)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveAnalytics() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.analytics(mapper.createObjectNode()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAnalytics)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemovePassthrough() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.passthrough(mapper.createObjectNode()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getPassthrough)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void processShouldRemoveKvps() {
        // given
        final BidderRequest bidderRequest = givenBidderRequest(
                extPrebid -> extPrebid.kvps(mapper.createObjectNode()));

        // when
        final Result<BidderRequest> result = target.process(bidderRequest, null, null).result();

        // then
        assertThat(result.getValue())
                .extracting(BidderRequest::getBidRequest)
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getKvps)
                .isNull();
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidderRequest givenBidderRequest(
            UnaryOperator<ExtRequestPrebid.ExtRequestPrebidBuilder> extPrebidCustomizer) {

        return BidderRequest.builder()
                .bidRequest(BidRequest.builder()
                        .ext(ExtRequest.of(extPrebidCustomizer.apply(ExtRequestPrebid.builder()).build()))
                        .build())
                .bidder(BIDDER)
                .build();
    }
}
