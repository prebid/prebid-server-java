package org.prebid.server.bidder.verizonmedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.verizonmedia.ExtImpVerizonmedia;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class VerizonmediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpVerizonmedia>> VERIZON_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpVerizonmedia>>() {
            };
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String endpointUrl;

    public VerizonmediaBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final List<Imp> impList = bidRequest.getImp();
        for (int i = 0; i < impList.size(); i++) {
            try {
                final Imp imp = impList.get(i);
                final ExtImpVerizonmedia extImpVerizonmedia = parseAndValidateImpExt(imp.getExt(), i);
                final BidRequest modifiedRequest = modifyRequest(bidRequest, imp, extImpVerizonmedia);
                bidRequests.add(makeHttpRequest(modifiedRequest));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(bidRequests, errors);
    }

    private static ExtImpVerizonmedia parseAndValidateImpExt(ObjectNode impExtNode, int index) {
        final ExtImpVerizonmedia extImpVerizonmedia;
        try {
            extImpVerizonmedia = Json.mapper.<ExtPrebid<?, ExtImpVerizonmedia>>convertValue(impExtNode,
                    VERIZON_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("imp #%s: %s", index, e.getMessage()));
        }

        final String dcn = extImpVerizonmedia.getDcn();
        if (StringUtils.isBlank(dcn)) {
            throw new PreBidException(String.format("imp #%s: missing param dcn", index));
        }

        final String pos = extImpVerizonmedia.getPos();
        if (StringUtils.isBlank(pos)) {
            throw new PreBidException(String.format("imp #%s: missing param pos", index));
        }

        return extImpVerizonmedia;
    }

    private static BidRequest modifyRequest(BidRequest request, Imp imp, ExtImpVerizonmedia extImpVerizonmedia) {
        final Banner banner = imp.getBanner();
        final boolean hasBanner = banner != null;

        final Integer bannerWidth = hasBanner ? banner.getW() : null;
        final Integer bannerHeight = hasBanner ? banner.getH() : null;
        final boolean hasBannerWidthAndHeight = bannerWidth != null && bannerHeight != null;

        if (hasBannerWidthAndHeight && (bannerWidth == 0 || bannerHeight == 0)) {
            throw new PreBidException(String.format(
                    "Invalid sizes provided for Banner %sx%s", bannerWidth, bannerHeight));
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(extImpVerizonmedia.getPos());

        if (hasBanner && !hasBannerWidthAndHeight) {
            impBuilder.banner(modifyBanner(banner));
        }

        final Site site = request.getSite();
        final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();

        return request.toBuilder()
                .imp(Collections.singletonList(impBuilder.build()))
                .site(siteBuilder.id(extImpVerizonmedia.getDcn()).build())
                .build();
    }

    private static Banner modifyBanner(Banner banner) {
        final List<Format> bannerFormats = banner.getFormat();
        if (CollectionUtils.isEmpty(bannerFormats)) {
            throw new PreBidException("No sizes provided for Banner");
        }
        final Format firstFormat = bannerFormats.get(0);

        return banner.toBuilder()
                .w(firstFormat.getW())
                .h(firstFormat.getH())
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest outgoingRequest) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .body(Json.encode(outgoingRequest))
                .headers(makeHeaders(outgoingRequest.getDevice()))
                .payload(outgoingRequest)
                .build();
    }

    private static MultiMap makeHeaders(Device device) {
        final String deviceUa = device != null ? device.getUa() : null;

        final MultiMap headers = HttpUtil.headers()
                .add("x-openrtb-version", "2.5");
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "User-Agent", deviceUa);

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        final List<SeatBid> seatbid = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (seatbid == null) {
            return Collections.emptyList();
        }

        if (seatbid.isEmpty()) {
            throw new PreBidException(String.format("Invalid SeatBids count: %d", seatbid.size()));
        }
        return bidsFromResponse(bidResponse, bidRequest.getImp());
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<Imp> imps) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(bid -> checkBid(bid.getImpid(), imps))
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    private static boolean checkBid(String bidImpId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(bidImpId)) {
                return imp.getBanner() != null;
            }
        }
        throw new PreBidException(String.format("Unknown ad unit code '%s'", bidImpId));
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
