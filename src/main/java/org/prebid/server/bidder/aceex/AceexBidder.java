package org.prebid.server.bidder.aceex;

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
import org.apache.commons.collections4.ListUtils;
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
import org.prebid.server.proto.openrtb.ext.request.aceex.ExtImpAceex;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AceexBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAceex>> ACEEX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAceex>>() {
            };
    private static final String ACCOUNT_ID_MACRO = "{{AccountId}}";
    private static final String X_OPENRTB_VERSION = "2.5";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AceexBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final Imp firstImp = request.getImp().get(0);
        try {
            final ExtImpAceex extImpAceex = mapper.mapper().convertValue(
                    firstImp.getExt(), ACEEX_EXT_TYPE_REFERENCE).getBidder();
            return Result.withValue(makeHttpRequest(request, extImpAceex.getAccountId()));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badInput("Ext.bidder not provided"));
        }
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String accountId) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(resolveEndpoint(accountId))
                .headers(constructHeaders(request))
                .body(mapper.encode(request))
                .payload(request)
                .build();
    }

    private static MultiMap constructHeaders(BidRequest bidRequest) {
        final Device device = bidRequest.getDevice();
        MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
                ObjectUtils.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtils.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtils.getIfNotNull(device, Device::getIp));

        return headers;
    }

    private String resolveEndpoint(String accountId) {
        return endpointUrl.replace(ACCOUNT_ID_MACRO, HttpUtil.encodeUrl(accountId));
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse("Bad Server Response"));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        final List<SeatBid> seatBids = ObjectUtils.getIfNotNull(bidResponse, BidResponse::getSeatbid);
        final SeatBid firstSeatBid = CollectionUtils.isNotEmpty(seatBids) ? seatBids.get(0) : null;
        if (firstSeatBid == null) {
            throw new PreBidException("Empty SeatBid array");
        }

        final List<Bid> bids = ListUtils.emptyIfNull(firstSeatBid.getBid());
        return bidsFromSeatBid(bids, bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromSeatBid(List<Bid> bids,
                                                   BidRequest bidRequest,
                                                   BidResponse bidResponse) {
        return bids.stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        return BidType.banner;
    }
}
