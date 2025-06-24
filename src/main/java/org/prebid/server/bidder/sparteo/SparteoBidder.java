package org.prebid.server.bidder.sparteo;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.sparteo.ExtImpSparteo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SparteoBidder implements Bidder<BidRequest> {

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
                final JsonNode bidderNode = imp.getExt().get("bidder");
                final ExtImpSparteo bidderParams = mapper.mapper().treeToValue(bidderNode, ExtImpSparteo.class);

                if (siteNetworkId == null && bidderParams.getNetworkId() != null) {
                    siteNetworkId = bidderParams.getNetworkId();
                }

                final ObjectNode modifiedExt = buildImpExt(imp, bidderParams, mapper);

                modifiedImps.add(imp.toBuilder().ext(modifiedExt).build());
            } catch (NullPointerException | JsonProcessingException e) {
                errors.add(BidderError.badInput(
                        String.format("ignoring imp id=%s, error processing ext: %s",
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

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final int statusCode = httpCall.getResponse().getStatusCode();

        if (statusCode == 204) {
            return Result.of(Collections.emptyList(), errors);
        }

        if (statusCode != 200) {
            return Result.withError(BidderError.badServerResponse(
                    String.format("HTTP status %d returned from Sparteo", statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(
                    String.format("Failed to decode Sparteo response: %s", e.getMessage())));
        }
    }

    private ObjectNode buildImpExt(Imp imp, ExtImpSparteo bidderParams, JacksonMapper mapper)
        throws JsonProcessingException {

        final ObjectNode extMap = mapper.mapper().convertValue(imp.getExt(), ObjectNode.class);

        extMap.remove("bidder");

        final JsonNode sparteoNode = extMap.get("sparteo");
        final ObjectNode outgoingParamsNode;

        if (sparteoNode != null && sparteoNode.isObject() && sparteoNode.has("params")
                && sparteoNode.get("params").isObject()) {
            outgoingParamsNode = (ObjectNode) sparteoNode.get("params");
        } else {
            outgoingParamsNode = extMap.putObject("sparteo").putObject("params");
        }

        final ObjectNode bidderParamsAsNode = mapper.mapper().convertValue(bidderParams, ObjectNode.class);
        outgoingParamsNode.setAll(bidderParamsAsNode);

        final JsonNode prebidNode = extMap.get("prebid");
        if (prebidNode != null && prebidNode.has("adunitcode")) {
            outgoingParamsNode.set("adUnitCode", prebidNode.get("adunitcode"));
        }

        return extMap;
    }

    private Site modifySite(Site site, String siteNetworkId, JacksonMapper mapper) {
        if (site == null || site.getPublisher() == null || siteNetworkId == null) {
            return site;
        }

        final Publisher publisher = site.getPublisher();
        final ExtPublisher extPublisher;

        extPublisher = publisher.getExt() != null
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

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> {
                    if (bid == null) {
                        errors.add(BidderError.badServerResponse("Received null bid object within a seatbid."));
                        return null;
                    }
                    return toBidderBid(bid, bidResponse.getCur(), errors);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidTypeFromBidExtension(bid);
            return BidderBid.of(bid, bidType, currency);
        } catch (Exception e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private BidType getBidTypeFromBidExtension(Bid bid) throws Exception {
        final ObjectNode bidExtNode = bid.getExt();

        if (bidExtNode == null || !bidExtNode.hasNonNull("prebid")) {
            throw new Exception(
                    String.format("Bid extension or bid.ext.prebid missing for impression id: %s",
                    bid.getImpid())
            );
        }

        final JsonNode prebidNode = bidExtNode.get("prebid");
        final ExtBidPrebid extBidPrebid;

        try {
            extBidPrebid = mapper.mapper().treeToValue(prebidNode, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            throw new Exception(
                    String.format("Failed to parse bid.ext.prebid for impression id: %s, error: %s",
                    bid.getImpid(),
                    e.getMessage()
                ),
            e);
        }

        if (extBidPrebid == null || extBidPrebid.getType() == null) {
            throw new Exception(
                    String.format("Missing type in bid.ext.prebid for impression id: %s",
                    bid.getImpid()
            ));
        }

        final BidType bidType = extBidPrebid.getType();
        if (bidType == BidType.audio) {
            throw new Exception(
                    String.format("Audio bid type not supported by this adapter for impression id: %s",
                    bid.getImpid())
            );
        }

        return bidType;
    }
}
