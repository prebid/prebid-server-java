package org.prebid.server.bidder.verizonmedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
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
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);

        final ExtImpVerizonmedia extImpVerizonmedia;
        try {
            extImpVerizonmedia = parseAndValidateImpExt(firstImp.getExt());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final MultiMap headers = makeHeaders(request.getDevice());

        final BidRequest outgoingRequest = modifyRequest(request, firstImp, extImpVerizonmedia);
        final String body = Json.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(headers)
                        .payload(outgoingRequest)
                        .build()),
                Collections.emptyList());
    }

    private static ExtImpVerizonmedia parseAndValidateImpExt(ObjectNode impExtNode) {
        final ExtImpVerizonmedia extImpVerizonmedia;
        try {
            extImpVerizonmedia = Json.mapper.<ExtPrebid<?, ExtImpVerizonmedia>>convertValue(impExtNode,
                    VERIZON_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final String dcn = extImpVerizonmedia.getDcn();
        if (StringUtils.isBlank(dcn)) {
            throw new PreBidException("Missing param dcn");
        }

        final String pos = extImpVerizonmedia.getPos();
        if (StringUtils.isBlank(pos)) {
            throw new PreBidException("Missing param pos");
        }

        return extImpVerizonmedia;
    }

    private static MultiMap makeHeaders(Device device) {
        final String deviceUa = device != null ? device.getUa() : null;

        final MultiMap headers = HttpUtil.headers()
                .add("x-openrtb-version", "2.5");
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, "User-Agent", deviceUa);

        return headers;
    }

    private static BidRequest modifyRequest(BidRequest request, Imp firstImp, ExtImpVerizonmedia extImpVerizonmedia) {
        final Site site = request.getSite();
        final String siteId = site != null ? site.getId() : null;

        if (StringUtils.isBlank(firstImp.getTagid()) || StringUtils.isBlank(siteId)) {
            final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder();

            if (StringUtils.isBlank(firstImp.getTagid())) {
                final List<Imp> imps = new ArrayList<>(request.getImp());
                imps.set(0, firstImp.toBuilder().tagid(extImpVerizonmedia.getPos()).build());
                requestBuilder.imp(imps);
            }

            if (StringUtils.isBlank(siteId)) {
                final Site.SiteBuilder siteBuilder = site == null ? Site.builder() : site.toBuilder();
                requestBuilder.site(siteBuilder.id(extImpVerizonmedia.getDcn()).build());
            }
            return requestBuilder.build();
        }
        return request;
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
