package org.prebid.server.bidder.ttx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.ttx.proto.TtxImpExt;
import org.prebid.server.bidder.ttx.proto.TtxImpExtTtx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ttx.ExtImpTtx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TtxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpTtx>> TTX_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpTtx>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public TtxBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final BidRequest outgoingRequest;
        try {
            outgoingRequest = createBidRequest(bidRequest);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.of(Collections.emptyList(), errors);
        }

        String body;
        try {
            body = Json.encode(outgoingRequest);
        } catch (EncodeException e) {
            errors.add(BidderError.badInput(String.format("Failed to encode request body, error: %s", e.getMessage())));
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(Collections.singletonList(
                HttpRequest.of(HttpMethod.POST, endpointUrl, body, BidderUtil.headers(), outgoingRequest)), errors);
    }

    private static BidRequest createBidRequest(BidRequest bidRequest) {

        final List<Imp> imps = bidRequest.getImp();
        final Imp firstImp = imps.get(0);

        final ExtImpTtx extImpTtx = parseImpExt(firstImp);

        final String zoneId = extImpTtx.getZoneId();
        final TtxImpExt ttxImpExt = TtxImpExt.of(
                TtxImpExtTtx.of(extImpTtx.getProductId(), StringUtils.isNotBlank(zoneId) ? zoneId : null));

        final Imp modifiedFirstImp = firstImp.toBuilder().ext(Json.mapper.valueToTree(ttxImpExt)).build();

        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        if (imps.size() == 1) {
            requestBuilder.imp(Collections.singletonList(modifiedFirstImp));
        } else {
            final List<Imp> subList = imps.subList(1, imps.size());
            final List<Imp> modifiedImps = new ArrayList<>(subList.size() + 1);
            modifiedImps.add(modifiedFirstImp);
            modifiedImps.addAll(subList);
            requestBuilder.imp(modifiedImps);
        }

        return requestBuilder
                .site(modifySite(bidRequest.getSite(), extImpTtx.getSiteId()))
                .build();
    }

    private static ExtImpTtx parseImpExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpTtx>>convertValue(imp.getExt(), TTX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Site modifySite(Site site, String siteId) {
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
        return siteBuilder
                .id(siteId)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
