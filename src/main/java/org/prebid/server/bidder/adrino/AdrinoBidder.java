package org.prebid.server.bidder.adrino;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adrino.ExtImpAdrino;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AdrinoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAdrino>> ADRINO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {

            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final String IMP_NOT_PROVIDED_MSG = "BidRequest.imp not provided";
    private static final String IMP_EXT_EMPTY_MSG = "Ignoring imp id=%s, extImpBidder is empty";
    private static final String HASH_REQUIRED_MSG = "Hash field required for bidder";
    private static final String NATIVE_ONLY_MSG = "Ignoring imp id=%s, Adrino supports only Native";

    public AdrinoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        List<BidderError> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(HttpUtil.headers())
                        .body(mapper.encodeToBytes(request))
                        .payload(request)
                        .build());
    }

    private List<BidderError> validateRequest(BidRequest request) {
        if (request.getImp() == null) {
            return Collections.singletonList(BidderError.badInput(IMP_NOT_PROVIDED_MSG));
        }
        return request.getImp().stream()
                .map(this::validateImpression)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<BidderError> validateImpression(Imp impression) {
        final String impId = impression.getId();
        final ObjectNode impExt = impression.getExt();
        if (impExt == null || impExt.isEmpty()) {
            return Optional.of(BidderError.badInput(String.format(IMP_EXT_EMPTY_MSG, impId)));
        }
        final ExtImpAdrino ext = getAdrinoExt(impExt);
        if (ext.getHash() == null) {
            return Optional.of(BidderError.badInput(HASH_REQUIRED_MSG));
        }
        if (impression.getXNative() == null) {
            return Optional.of(BidderError.badInput(String.format(NATIVE_ONLY_MSG, impId)));
        }
        return Optional.empty();
    }

    private ExtImpAdrino getAdrinoExt(ObjectNode impExt) {
        return mapper.mapper().convertValue(impExt, ADRINO_EXT_TYPE_REFERENCE).getBidder();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException e) {
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
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.xNative, bidResponse.getCur()))
                .toList();
    }
}
