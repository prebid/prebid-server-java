package org.prebid.server.bidder.smartyads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.smartyads.ExtImpSmartyAds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SmartyAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartyAds>> SMARTYADS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_SOURCE_ID_MACRO = "{{SourceId}}";
    private static final String URL_ACCOUNT_ID_MACRO = "{{AccountID}}";
    private static final int FIRST_SEAT_BID_INDEX = 0;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmartyAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> validImps = new ArrayList<>();

        ExtImpSmartyAds extImpSmartyAds = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpSmartyAds = parseImpExt(imp);
                validImps.add(updateImp(imp));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        return Result.withValues(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveUrl(extImpSmartyAds))
                        .headers(resolveHeaders(request.getDevice()))
                        .payload(outgoingRequest)
                        .body(mapper.encodeToBytes(outgoingRequest))
                        .build()));
    }

    private ExtImpSmartyAds parseImpExt(Imp imp) {
        final ExtImpSmartyAds extImpSmartyAds;
        try {
            extImpSmartyAds = mapper.mapper().convertValue(imp.getExt(), SMARTYADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
        if (StringUtils.isBlank(extImpSmartyAds.getHost())) {
            throw new PreBidException("host is a required ext.bidder param");
        }
        if (StringUtils.isBlank(extImpSmartyAds.getAccountId())) {
            throw new PreBidException("accountId is a required ext.bidder param");
        }
        if (StringUtils.isBlank(extImpSmartyAds.getSourceId())) {
            throw new PreBidException("sourceId is a required ext.bidder param");
        }
        return extImpSmartyAds;
    }

    private static Imp updateImp(Imp imp) {
        return imp.toBuilder().ext(null).build();
    }

    private String resolveUrl(ExtImpSmartyAds extImp) {
        return endpointUrl
                .replace(URL_HOST_MACRO, extImp.getHost())
                .replace(URL_SOURCE_ID_MACRO, HttpUtil.encodeUrl(extImp.getSourceId()))
                .replace(URL_ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(extImp.getAccountId()));
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());

            if (device.getDnt() != null) {
                HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, device.getDnt().toString());
            }
        }
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(httpCall), Collections.emptyList());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(HttpCall<BidRequest> httpCall) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException("Bad Server Response");
        }
        if (bidResponse == null) {
            throw new PreBidException("Bad Server Response");
        }
        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(httpCall.getRequest().getPayload(), bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(FIRST_SEAT_BID_INDEX);
        return CollectionUtils.emptyIfNull(firstSeatBid.getBid()).stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
