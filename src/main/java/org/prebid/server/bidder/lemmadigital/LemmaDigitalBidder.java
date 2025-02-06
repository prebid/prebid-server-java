package org.prebid.server.bidder.lemmadigital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.lemmadigital.ExtImpLemmaDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LemmaDigitalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLemmaDigital>> LEMMA_DIGITAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String AD_UNIT_MACRO = "{{AdUnit}}";
    private static final String PUBLISHER_ID_MACRO = "{{PublisherID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LemmaDigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.withError(BidderError.badInput("Impression array should not be empty"));
        }

        final Imp imp = bidRequest.getImp().getFirst();
        final ExtImpLemmaDigital extImpLemmaDigital;

        try {
            extImpLemmaDigital = parseImpExt(imp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        return Result.withValue(createRequest(extImpLemmaDigital, bidRequest));
    }

    private ExtImpLemmaDigital parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), LEMMA_DIGITAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Invalid imp.ext.bidder for impression index 0. "
                    + "Error Infomation: %s", imp.getId()));
        }
    }

    private HttpRequest<BidRequest> createRequest(ExtImpLemmaDigital extImpLemmaDigital, BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveUrl(extImpLemmaDigital))
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(bidRequest))
                .payload(bidRequest)
                .body(mapper.encodeToBytes(bidRequest))
                .build();
    }

    private String resolveUrl(ExtImpLemmaDigital extImpLemmaDigital) {
        return endpointUrl
                .replace(AD_UNIT_MACRO, String.valueOf(extImpLemmaDigital.getAid()))
                .replace(PUBLISHER_ID_MACRO, String.valueOf(extImpLemmaDigital.getPid()));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidRequest, bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final BidType bidType = resolveBidType(bidRequest);
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType, bidResponse.getCur()))
                .toList();
    }

    private static BidType resolveBidType(BidRequest bidRequest) {
        return bidRequest.getImp().getFirst().getVideo() != null ? BidType.video : BidType.banner;
    }
}
