package org.prebid.server.bidder.nextmillenium;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.nextmillenium.ExtImpNextMillenium;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class NextMilleniumBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNextMillenium>> NEXTMILLENIUM_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NextMilleniumBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<ExtImpNextMillenium> impExts = getImpExts(bidRequest, errors);

        return errors.isEmpty()
                ? Result.withValues(makeRequests(bidRequest, impExts))
                : Result.withErrors(errors);
    }

    private List<ExtImpNextMillenium> getImpExts(BidRequest bidRequest, List<BidderError> errors) {
        final List<ExtImpNextMillenium> impExts = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                impExts.add(mapper.mapper()
                        .convertValue(imp.getExt(), NEXTMILLENIUM_EXT_TYPE_REFERENCE)
                        .getBidder());
            } catch (IllegalArgumentException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        return impExts;
    }

    private List<HttpRequest<BidRequest>> makeRequests(BidRequest bidRequest, List<ExtImpNextMillenium> extImps) {
        return extImps.stream()
                .map(extImp -> makeHttpRequest(updateBidRequest(bidRequest, extImp)))
                .collect(Collectors.toList());
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, ExtImpNextMillenium ext) {
        return BidRequest.builder()
                .id(bidRequest.getId())
                .test(bidRequest.getTest())
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .storedrequest(ExtStoredRequest.of(ext.getPlacementId()))
                                .build()))
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(headers())
                .payload(bidRequest)
                .body(mapper.encodeToBytes(bidRequest))
                .build();
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
                return Result.empty();
            }
            return Result.withValues(bidsFromResponse(bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
