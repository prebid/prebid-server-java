package org.prebid.server.bidder.logicad;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.logicad.ExtImpLogicad;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class LogicadBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLogicad>> LOGICAD_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpLogicad>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LogicadBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        try {
            final Map<ExtImpLogicad, List<Imp>> impToExtImp = getExtImpToImps(request, errors);
            httpRequests.addAll(buildAdapterRequests(request, impToExtImp));
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(httpRequests, errors);
    }

    private Map<ExtImpLogicad, List<Imp>> getExtImpToImps(BidRequest request, List<BidderError> errors) {
        final Map<ExtImpLogicad, List<Imp>> result = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpLogicad extImpLogicad = parseAndValidateImpExt(imp);
                final Imp updatedImp = updateImp(imp, extImpLogicad.getTid());

                result.putIfAbsent(extImpLogicad, new ArrayList<>());
                result.get(extImpLogicad).add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (result.isEmpty()) {
            throw new PreBidException("No appropriate impressions");
        }

        return result;
    }

    private ExtImpLogicad parseAndValidateImpExt(Imp imp) {
        final ExtImpLogicad extImp;
        try {
            extImp = mapper.mapper().convertValue(imp.getExt(), LOGICAD_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImp.getTid())) {
            throw new PreBidException("No tid value provided");
        }

        return extImp;
    }

    private static Imp updateImp(Imp imp, String tid) {
        return imp.toBuilder().tagid(tid).ext(null).build();
    }

    private List<HttpRequest<BidRequest>> buildAdapterRequests(BidRequest bidRequest,
                                                               Map<ExtImpLogicad, List<Imp>> extImpToImps) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Map.Entry<ExtImpLogicad, List<Imp>> entry : extImpToImps.entrySet()) {
            final BidRequest updatedBidRequest = bidRequest.toBuilder().imp(entry.getValue()).build();

            final HttpRequest<BidRequest> createdBidRequest = HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(endpointUrl)
                    .body(mapper.encodeToBytes(updatedBidRequest))
                    .headers(HttpUtil.headers())
                    .payload(bidRequest)
                    .build();

            httpRequests.add(createdBidRequest);
        }

        return httpRequests;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
