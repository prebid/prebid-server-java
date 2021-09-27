package org.prebid.server.bidder.acuityads;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.acuity.ExtImpAcuityads;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AcuityadsBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAcuityads>> ACUITYADS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAcuityads>>() {
            };
    private static final String OPENRTB_VERSION = "2.5";
    private static final String URL_HOST_MACRO = "{{Host}}";
    private static final String URL_ACCOUNT_ID_MACRO = "{{AccountID}}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AcuityadsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpAcuityads extImpAcuityads;
        final String url;

        try {
            extImpAcuityads = parseImpExt(request.getImp().get(0));
            url = resolveEndpoint(extImpAcuityads.getHost(), extImpAcuityads.getAccountId());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(removeFirstImpExt(request.getImp()))
                .build();

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(url)
                                .headers(resolveHeaders(request.getDevice()))
                                .payload(outgoingRequest)
                                .body(mapper.encode(outgoingRequest))
                                .build()),
                Collections.emptyList());
    }

    private ExtImpAcuityads parseImpExt(Imp imp) {
        final ExtImpAcuityads extImpAcuityads;
        try {
            extImpAcuityads = mapper.mapper().convertValue(imp.getExt(), ACUITYADS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }
        if (extImpAcuityads == null) {
            throw new PreBidException("ext.bidder not provided");
        }
        if (StringUtils.isBlank(extImpAcuityads.getHost())) {
            throw new PreBidException("Missed host param");
        }
        if (StringUtils.isBlank(extImpAcuityads.getAccountId())) {
            throw new PreBidException("Missed accountId param");
        }
        return extImpAcuityads;
    }

    private String resolveEndpoint(String host, String accountId) {
        return endpointUrl
                .replace(URL_HOST_MACRO, StringUtils.stripToEmpty(host))
                .replace(URL_ACCOUNT_ID_MACRO, StringUtils.stripToEmpty(accountId));
    }

    private static List<Imp> removeFirstImpExt(List<Imp> imps) {
        return IntStream.range(0, imps.size())
                .mapToObj(impIndex -> impIndex == 0
                        ? imps.get(impIndex).toBuilder().ext(null).build()
                        : imps.get(impIndex))
                .collect(Collectors.toList());
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null) {
            throw new PreBidException("Bad Server Response");
        }
        if (CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid array");
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(0);
        final List<Bid> bids = firstSeatBid.getBid();

        if (CollectionUtils.isEmpty(bids)) {
            throw new PreBidException("Empty bids array");
        }

        return bids.stream()
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
