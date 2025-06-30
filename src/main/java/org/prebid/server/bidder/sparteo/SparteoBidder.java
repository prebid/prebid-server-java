package org.prebid.server.bidder.sparteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.sparteo.ExtImpSparteo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SparteoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSparteo>> TYPE_REFERENCE =
            new TypeReference<>() { };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SparteoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        String siteNetworkId = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpSparteo bidderParams = parseExtImp(imp);

                if (siteNetworkId == null && bidderParams.getNetworkId() != null) {
                    siteNetworkId = bidderParams.getNetworkId();
                }

                final ObjectNode modifiedExt = modifyImpExt(imp);

                modifiedImps.add(imp.toBuilder().ext(modifiedExt).build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(
                        "ignoring imp id=%s, error processing ext: %s".formatted(
                                imp.getId(), e.getMessage())));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(modifiedImps)
                .site(modifySite(request.getSite(), siteNetworkId, mapper))
                .build();

        final HttpRequest<BidRequest> call = BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);

        return Result.of(Collections.singletonList(call), errors);
    }

    private ExtImpSparteo parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid imp.ext");
        }
    }

    private static ObjectNode modifyImpExt(Imp imp) {
        final ObjectNode modifiedImpExt = imp.getExt().deepCopy();
        final JsonNode sparteoJsonNode = modifiedImpExt.get("sparteo");
        final ObjectNode sparteoNode = sparteoJsonNode == null || !sparteoJsonNode.isObject()
                ? modifiedImpExt.putObject("sparteo")
                : (ObjectNode) sparteoJsonNode;

        final JsonNode paramsJsonNode = sparteoNode.get("params");
        final ObjectNode paramsNode = paramsJsonNode == null || !paramsJsonNode.isObject()
                ? sparteoNode.putObject("params")
                : (ObjectNode) paramsJsonNode;

        final JsonNode bidderJsonNode = modifiedImpExt.remove("bidder");
        if (bidderJsonNode != null && bidderJsonNode.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> fields = bidderJsonNode.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                paramsNode.set(field.getKey(), field.getValue());
            }
        }
        return modifiedImpExt;
    }

    private Site modifySite(Site site, String siteNetworkId, JacksonMapper mapper) {
        if (site == null || site.getPublisher() == null || siteNetworkId == null) {
            return site;
        }

        final Publisher publisher = site.getPublisher();

        final ExtPublisher extPublisher = publisher.getExt() != null
                ? publisher.getExt()
                : ExtPublisher.empty();

        final JsonNode paramsProperty = extPublisher.getProperty("params");
        final ObjectNode paramsNode;

        if (paramsProperty != null && paramsProperty.isObject()) {
            paramsNode = (ObjectNode) paramsProperty;
        } else {
            paramsNode = mapper.mapper().createObjectNode();
            extPublisher.addProperty("params", paramsNode);
        }

        paramsNode.put("networkId", siteNetworkId);

        final Publisher modifiedPublisher = publisher.toBuilder()
                .ext(extPublisher)
                .build();

        return site.toBuilder()
                .publisher(modifiedPublisher)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidType getBidType(Bid bid) throws PreBidException {
        final BidType bidType = Optional.ofNullable(bid.getExt())
                .map(ext -> ext.get("prebid"))
                .filter(JsonNode::isObject)
                .map(this::parseExtBidPrebid)
                .map(ExtBidPrebid::getType)
                .orElseThrow(() -> new PreBidException(
                        "Failed to parse bid mediatype for impression \"%s\"".formatted(bid.getImpid())));

        if (bidType == BidType.audio) {
            throw new PreBidException(
                    "Audio bid type not supported by this adapter for impression id: %s".formatted(bid.getImpid()));
        }

        return bidType;
    }

    private ExtBidPrebid parseExtBidPrebid(JsonNode prebidNode) {
        try {
            return mapper.mapper().treeToValue(prebidNode, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid);

            final Integer mtype = switch (bidType) {
                case banner -> 1;
                case video -> 2;
                case xNative -> 4;
                default -> null;
            };

            final Bid bidWithMtype = mtype != null ? bid.toBuilder().mtype(mtype).build() : bid;

            return BidderBid.of(bidWithMtype, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }
}
