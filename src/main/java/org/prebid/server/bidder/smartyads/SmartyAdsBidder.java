package org.prebid.server.bidder.smartyads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SmartyAdsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmartyAds>> SMARTYADS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpSmartyAds>>() {
            };
    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_SOURCE_ID_MACRO = "{{SourceId}}";
    private static final String URL_ACCOUNT_ID_MACRO = "{{AccountID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SmartyAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        ExtImpSmartyAds extImpSmartyAds = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpSmartyAds = parseImpExt(imp);
                validImps.add(updateImp(imp));
            } catch (PreBidException e) {
                return Result.emptyWithError(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveUrl(extImpSmartyAds))
                        .headers(resolveHeaders(request.getDevice()))
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
                        .build()), errors);
    }

    private ExtImpSmartyAds parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), SMARTYADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
    }

    private static Imp updateImp(Imp imp) {
        return imp.toBuilder().ext(null).build();
    }

    private String resolveUrl(ExtImpSmartyAds extImp) {
        return endpointUrl
                .replace(URL_HOST_MACRO, extImp.getHost())
                .replace(URL_SOURCE_ID_MACRO, extImp.getSourceId())
                .replace(URL_ACCOUNT_ID_MACRO, extImp.getAccountId());
    }

    private MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        headers.add("X-Openrtb-Version", "2.5");

        if (device != null) {
            if (StringUtils.isNotBlank(device.getUa())) {
                headers.add("User-Agent", device.getUa());
            }
            if (StringUtils.isNotBlank(device.getIpv6())) {
                headers.add("X-Forwarded-For", device.getIpv6());
            }
            if (StringUtils.isNotBlank(device.getIp())) {
                headers.add("X-Forwarded-For", device.getIp());
            }
            if (StringUtils.isNotBlank(device.getLanguage())) {
                headers.add("Accept-Language", device.getLanguage());
            }
            if (device.getDnt() != null) {
                headers.add("Dnt", device.getDnt().toString());
            }
        }
        return headers;
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(0);
        return firstSeatBid.getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    protected BidType getBidType(String impId, List<Imp> imps) {
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

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
