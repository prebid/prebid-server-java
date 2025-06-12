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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class SparteoBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SparteoBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static <T> Iterable<T> iterable(Iterator<T> it) {
        return () -> it;
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();

        String siteNetworkId = null;
        final List<Imp> modifiedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ObjectNode extMap = mapper.mapper()
                        .convertValue(imp.getExt(), ObjectNode.class);

                final ObjectNode bidderNode = (ObjectNode) extMap.remove("bidder");

                if (bidderNode != null) {
                    if (siteNetworkId == null && bidderNode.hasNonNull("networkId")) {
                        siteNetworkId = bidderNode.get("networkId").asText();
                    }

                    final ObjectNode sparteoNode = extMap.has("sparteo") && extMap.get("sparteo").isObject()
                            ? (ObjectNode) extMap.get("sparteo")
                            : extMap.putObject("sparteo");
                    final ObjectNode paramsNode = sparteoNode.has("params") && sparteoNode.get("params").isObject()
                            ? (ObjectNode) sparteoNode.get("params")
                            : sparteoNode.putObject("params");

                    for (String field : iterable(bidderNode.fieldNames())) {
                        paramsNode.set(field, bidderNode.get(field));
                    }
                }

                modifiedImps.add(imp.toBuilder().ext(extMap).build());
            } catch (Exception e) {
                errors.add(BidderError.badInput(
                        String.format("ignoring imp id=%s, error processing ext: %s",
                                    imp.getId(), e.getMessage())));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest.BidRequestBuilder rb = request.toBuilder().imp(modifiedImps);

        final Site site = request.getSite();
        if (site != null && site.getPublisher() != null && siteNetworkId != null) {
            final Publisher pub = site.getPublisher();

            final ObjectNode pubExtRaw = pub.getExt() != null
                    ? mapper.mapper().convertValue(pub.getExt(), ObjectNode.class)
                    : mapper.mapper().createObjectNode();

            pubExtRaw.withObjectProperty("params").put("networkId", siteNetworkId);

            final ExtPublisher extPub = mapper.mapper()
                    .convertValue(pubExtRaw, ExtPublisher.class);

            final Publisher newPub = pub.toBuilder().ext(extPub).build();
            final Site newSite = site.toBuilder().publisher(newPub).build();
            rb.site(newSite);
        }

        final BidRequest outgoing = rb.build();

        final HttpRequest<BidRequest> call = HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .impIds(BidderUtil.impIds(outgoing))
                .body(mapper.encodeToBytes(outgoing))
                .payload(outgoing)
                .build();

        final List<HttpRequest<BidRequest>> calls = Collections.singletonList(call);

        return errors.isEmpty()
                ? Result.withValues(calls)
                : Result.of(calls, errors);
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

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final int status = httpCall.getResponse().getStatusCode();
        if (status == 204) {
            return Result.of(Collections.emptyList(), errors);
        }
        if (status != 200) {
            errors.add(BidderError.badServerResponse(
                    String.format("HTTP status %d returned from Sparteo", status))
            );
            return Result.of(Collections.emptyList(), errors);
        }

        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            errors.add(BidderError.badServerResponse(
                    String.format("Failed to decode Sparteo response: %s", e.getMessage()))
            );
            return Result.of(Collections.emptyList(), errors);
        }

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.of(Collections.emptyList(), errors);
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        final String currency = bidResponse.getCur();

        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            if (seatBid != null && CollectionUtils.isNotEmpty(seatBid.getBid())) {
                for (Bid bid : seatBid.getBid()) {
                    if (bid == null) {
                        errors.add(BidderError.badServerResponse(
                                "Received null bid object within a seatbid.")
                        );
                        continue;
                    }
                    try {
                        final BidType type = getBidTypeFromBidExtension(bid);
                        bidderBids.add(BidderBid.of(bid, type, currency));
                    } catch (Exception e) {
                        errors.add(BidderError.badServerResponse(e.getMessage()));
                    }
                }
            }
        }

        return Result.of(bidderBids, errors);
    }
}
