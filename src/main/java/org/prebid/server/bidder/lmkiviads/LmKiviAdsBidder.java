package org.prebid.server.bidder.lmkiviads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.lmkiviads.ExtImpLmKiviAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LmKiviAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpLmKiviAds>> LMKIVIADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String HOST_MACRO = "{{Host}}";
    private static final String SOURCE_ID_MACRO = "{{SourceId}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public LmKiviAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpLmKiviAds extImpLmKiviads;
            try {
                extImpLmKiviads = parseImpExt(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            httpRequests.add(makeHttpRequest(request, resolveUrl(extImpLmKiviads)));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpLmKiviAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), LMKIVIADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private String resolveUrl(ExtImpLmKiviAds extImpLmKiviads) {
        return endpointUrl
                .replace(HOST_MACRO, extImpLmKiviads.getEnv())
                .replace(SOURCE_ID_MACRO, extImpLmKiviads.getPid());
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String endpointUrl) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .impIds(BidderUtil.impIds(request))
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encodeToBytes(request))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolvedBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid resolvedBidderBid(Bid bid, String cur, List<BidderError> errors) {
        final Optional<Object> type = Optional.ofNullable(bid.getExt())
                .map(bidExt -> bidExt.get("prebid"))
                .map(prebid -> prebid.get("type"));

        if (type.isEmpty()) {
            errors.add(BidderError.badServerResponse("Path to bid.ext.prebid.type doesn't specified"));
            return null;
        }

        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(type.get(), BidType.class);
        } catch (IllegalArgumentException e) {
            errors.add(BidderError.badServerResponse(String.format("Type expects one of the following values: "
                    + "'banner', 'native', 'video', 'audio' but got %s", type.get())));
            return null;
        }

        return BidderBid.of(bid, bidType, cur);
    }
}
