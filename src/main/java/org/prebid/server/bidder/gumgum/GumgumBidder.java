package org.prebid.server.bidder.gumgum;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.EncodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgum;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GumgumBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpGumgum>> GUMGUM_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpGumgum>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public GumgumBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final BidRequest outgoingRequest;
        try {
            outgoingRequest = createBidRequest(bidRequest, errors);
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

    private static BidRequest createBidRequest(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> modifiedImps = new ArrayList<>();
        String trackingId = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpGumgum impExt = parseImpExt(imp);
                if (imp.getBanner() != null) {
                    modifiedImps.add(modifyImp(imp));
                    trackingId = impExt.getZone();
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (modifiedImps.isEmpty()) {
            throw new PreBidException("No valid impressions");
        }

        final Site modifiedSite = modifySite(bidRequest.getSite(), trackingId);

        return bidRequest.toBuilder()
                .imp(modifiedImps)
                .site(modifiedSite)
                .build();
    }

    private static ExtImpGumgum parseImpExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpGumgum>>convertValue(imp.getExt(),
                    GUMGUM_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp modifyImp(Imp imp) {
        final Banner banner = imp.getBanner();
        final List<Format> format = banner.getFormat();
        if (banner.getH() == null && banner.getW() == null && CollectionUtils.isNotEmpty(format)) {
            final Format firstFormat = format.get(0);
            final Banner modifiedBanner = banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
            return imp.toBuilder()
                    .banner(modifiedBanner)
                    .build();
        }
        return imp;
    }

    private static Site modifySite(Site site, String trackingId) {
        if (site != null) {
            return site.toBuilder().id(trackingId).build();
        }
        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
