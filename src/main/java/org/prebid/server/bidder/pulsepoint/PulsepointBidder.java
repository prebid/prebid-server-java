package org.prebid.server.bidder.pulsepoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.pulsepoint.ExtImpPulsepoint;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pulsepoint {@link Bidder} implementation.
 */
public class PulsepointBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpPulsepoint>> PULSEPOINT_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpPulsepoint>>() {
            };

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public PulsepointBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();
        String publisherId = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                validateImp(imp);
                final ExtImpPulsepoint extImpPulsepoint = parseAndValidateImpExt(imp);
                modifiedImps.add(modifyImp(imp, extImpPulsepoint));
                publisherId = String.valueOf(extImpPulsepoint.getPublisherId());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (modifiedImps.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        requestBuilder.imp(modifiedImps);

        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();
        if (site != null) {
            requestBuilder.site(modifySite(site, publisherId));
        } else if (app != null) {
            requestBuilder.app(modifyApp(app, publisherId));
        }

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.of(HttpMethod.POST, endpointUrl, body, BidderUtil.headers(), outgoingRequest)), errors);
    }

    private static void validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException(String.format("Invalid MediaType. Pulsepoint supports only Banner type. "
                    + "Ignoring ImpID=%s", imp.getId()));
        }
    }

    private static ExtImpPulsepoint parseAndValidateImpExt(Imp imp) {
        ExtImpPulsepoint extImpPulsepoint;
        try {
            extImpPulsepoint = Json.mapper.<ExtPrebid<?, ExtImpPulsepoint>>convertValue(imp.getExt(),
                    PULSEPOINT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final Integer publisherId = extImpPulsepoint.getPublisherId();
        if (publisherId == null || publisherId == 0) {
            throw new PreBidException("Missing PublisherId param cp");
        }
        final Integer tagId = extImpPulsepoint.getTagId();
        if (tagId == null || tagId == 0) {
            throw new PreBidException("Missing TagId param ct");
        }
        final String adSize = extImpPulsepoint.getAdSize();
        if (StringUtils.isEmpty(adSize)) {
            throw new PreBidException("Missing AdSize param cf");
        }
        if (adSize.toLowerCase().split("x").length != 2) {
            throw new PreBidException(String.format("Invalid AdSize param %s", adSize));
        }

        return extImpPulsepoint;
    }

    private static Imp modifyImp(Imp imp, ExtImpPulsepoint extImpPulsepoint) {
        final String[] sizes = extImpPulsepoint.getAdSize().toLowerCase().split("x");
        final int width;
        final int height;
        try {
            width = Integer.parseInt(sizes[0]);
            height = Integer.parseInt(sizes[1]);
        } catch (NumberFormatException e) {
            throw new PreBidException(String.format("Invalid Width or Height param %s x %s", sizes[0], sizes[1]));
        }
        final Banner modifiedBanner = imp.getBanner().toBuilder().w(width).h(height).build();

        return imp.toBuilder()
                .tagid(String.valueOf(extImpPulsepoint.getTagId()))
                .banner(modifiedBanner)
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return site.toBuilder()
                .publisher(site.getPublisher() == null
                        ? Publisher.builder().id(publisherId).build()
                        : site.getPublisher().toBuilder().id(publisherId).build())
                .build();
    }

    private static App modifyApp(App app, String publisherId) {
        return app.toBuilder()
                .publisher(app.getPublisher() == null
                        ? Publisher.builder().id(publisherId).build()
                        : app.getPublisher().toBuilder().id(publisherId).build())
                .build();
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
