package org.prebid.server.bidder.nativo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSdk;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSdkRenderer;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NativoBidder implements Bidder<BidRequest> {

    private static final String NATIVO_RENDERER_NAME = "NativoRenderer";
    private static final String PREBID_EXT = "prebid";
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NativoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrlSyntax(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public final Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        return Result.withValue(BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final List<BidderError> errors = new ArrayList<>();
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse, List<BidderError> errors) {
        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> seatBid.getBid().stream()
                        .filter(Objects::nonNull)
                        .map(bid -> updateBid(bid, bidRequest, errors))
                        .filter(Objects::nonNull)
                        .map(bid -> BidderBid.of(bid, BidderUtil.getBidType(bid, impMap), bidResponse.getCur()))
                        .toList())
                .flatMap(Collection::stream)
                .toList();
    }

    private Bid updateBid(Bid bid, BidRequest bidRequest, List<BidderError> errors) {
        final ObjectNode updateBidExt;
        try {
            updateBidExt = prepareBidExt(bid, bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return bid;
        }

        return bid.toBuilder()
                .ext(updateBidExt)
                .build();
    }

    private ObjectNode prepareBidExt(Bid bid, BidRequest bidRequest) {
        final ObjectNode bidExt = bid.getExt();

        final String nativoRendererVersion = getNativoRendererVersion(bidRequest);
        if (nativoRendererVersion != null) {

            final ExtPrebid<ExtBidPrebid, ObjectNode> extPrebid = getExtPrebid(bidExt, bid.getId());
            final ExtBidPrebid extBidPrebid = extPrebid != null ? extPrebid.getPrebid() : null;

            final ExtBidPrebidMeta meta = Optional.ofNullable(extBidPrebid)
                    .map(ExtBidPrebid::getMeta)
                    .orElse(null);

            final ExtBidPrebidMeta updatedMeta = Optional.ofNullable(meta)
                    .map(ExtBidPrebidMeta::toBuilder)
                    .orElseGet(ExtBidPrebidMeta::builder)
                    .rendererVersion(nativoRendererVersion)
                    .build();

            final ExtBidPrebid modifiedExtBidPrebid = extBidPrebid != null
                    ? extBidPrebid.toBuilder().meta(updatedMeta).build()
                    : ExtBidPrebid.builder().meta(updatedMeta).build();

            final ObjectNode updatedBidExt = Optional.ofNullable(bidExt).orElseGet(mapper.mapper()::createObjectNode);
            updatedBidExt.set(PREBID_EXT, mapper.mapper().valueToTree(modifiedExtBidPrebid));
            return updatedBidExt;
        }

        return bidExt;
    }

    private String getNativoRendererVersion(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getSdk)
                .map(ExtRequestPrebidSdk::getRenderers)
                .orElse(Collections.emptyList())
                .stream()
                .filter(renderer -> NATIVO_RENDERER_NAME.equals(renderer.getName()))
                .map(ExtRequestPrebidSdkRenderer::getVersion)
                .findFirst()
                .orElse(null);
    }

    private ExtPrebid<ExtBidPrebid, ObjectNode> getExtPrebid(ObjectNode bidExt, String bidId) {
        try {
            return bidExt != null ? mapper.mapper().convertValue(bidExt, EXT_PREBID_TYPE_REFERENCE) : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid ext passed in bid with id: " + bidId);
        }
    }

}
