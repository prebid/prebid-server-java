package org.prebid.server.bidder.connatix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.connatix.proto.ConnatixImpExtBidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.connatix.ExtImpConnatix;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConnatixBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpConnatix>> CONNATIX_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private static final int MAX_IMPS_PER_REQUEST = 1;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final String PREBID_KEY = "prebid";
    private static final String SOURCE_PROPERTY = "source";
    private static final String VERSION_PROPERTY = "version";

    public ConnatixBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        // Device IP required - bounce if not available
        if (request.getDevice() == null ||
                (request.getDevice().getIp() == null && request.getDevice().getIpv6() == null)) {
            return Result.withError(BidderError.badInput("Device IP is required"));
        }

        // KATIE TO DO. UPDATE THIS LOGIC TO PAY ATTENTION TO display manager.
        // display manager version can come from openrtb2 request OR imp.ext.prebid
        String displayManagerVer = buildDisplayManagerVersion(request);

        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpConnatix extImpConnatix;
            try {
                extImpConnatix = parseExtImp(imp);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
            // KATIE to do - probably need to add logic for splitting requests somewhere in here

            final Imp modifiedImp = modifyImp(imp, extImpConnatix);
            httpRequests.add(makeHttpRequest(request, modifiedImp));
        }

        return Result.withValues(httpRequests);

    }

    private Imp modifyImp(Imp imp, ExtImpConnatix extImpConnatix) {
        //KATIE to do - fix this method, it isn't right :)
        final ConnatixImpExtBidder impExtBidder = resolveImpExt(extImpConnatix);

        final ObjectNode impExtBidderNode = mapper.mapper().valueToTree(impExtBidder);

        final ObjectNode modifiedImpExtBidder = imp.getExt() != null ? imp.getExt().deepCopy() : mapper.mapper().createObjectNode();

        modifiedImpExtBidder.setAll(impExtBidderNode);

        return imp.toBuilder().ext(modifiedImpExtBidder).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            // KATIE check validity of this logic for setting currency to usd. explicitly set to USD in go version
            final BidResponse updatedResponse = bidResponse.toBuilder().cur("USD").build();
            final List<BidderBid> bids = extractBids(httpCall.getRequest().getPayload(), updatedResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    // extract bids
    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }


    // parseExtImp
    private ExtImpConnatix parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), CONNATIX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ConnatixImpExtBidder resolveImpExt(ExtImpConnatix extImpConnatix) {

        final ConnatixImpExtBidder.ConnatixImpExtBidderBuilder builder = ConnatixImpExtBidder.builder();
        // KATIE check this is correct - adding placement ID and viewability percentage if available
        if (StringUtils.isNotEmpty(extImpConnatix.getPlacementId())) {
            builder.placementId(extImpConnatix.getPlacementId());
        }
        if (extImpConnatix.getViewabilityPercentage() != null) {
            builder.viewabilityPercentage(extImpConnatix.getViewabilityPercentage());
        }

        return builder.build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder().imp(List.of(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }


    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, List<Imp> imps, String url) {
        return BidderUtil.defaultRequest(bidRequest.toBuilder().imp(imps).build(), url, mapper);
    }


    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String url) {

        // KATIE TO DO: this is how we've done it elsewhere but go version has logic that
        // explicitly modifies headers here - need to figure out what that's about
        return ListUtils.partition(imps, MAX_IMPS_PER_REQUEST)
                .stream()
                .map(impsChunk -> createHttpRequest(bidRequest, impsChunk, url))
                .toList();
    }


    // check display manager version
    private String buildDisplayManagerVersion(BidRequest request) {
        if (request.getApp() == null || request.getApp().getExt() == null) {
            return "";
        }

        try {
            JsonNode extNode = mapper.mapper().readTree(String.valueOf(request.getApp().getExt()));
            JsonNode prebidNode = extNode.path(PREBID_KEY);

            String source = prebidNode.path(SOURCE_PROPERTY).asText("");
            String version = prebidNode.path(VERSION_PROPERTY).asText("");

            return (StringUtils.isNotEmpty(source) && StringUtils.isNotEmpty(version))
                    ? source + "-" + version
                    : "";

        } catch (Exception e) {
            return "";
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                // KATIE TO DO - this is how go version gets mediaType - validate this
                String mediaType = imp.getExt().get("cnx").get("mediaType").asText();
                if (mediaType.equals("video")) {
                    return BidType.video;
                } return BidType.banner;
            }
            break;
        }
        throw new PreBidException(String.format("Failed to find impression for ID: '%s'", impId));
    }
}
