package org.prebid.server.bidder.adtrgtme;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AdtrgtmeBidder implements Bidder<BidRequest> {

    private static final String X_OPENRTB_VERSION = "2.5";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AdtrgtmeBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final BidRequest updatedRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

            try {
                String requestUri = resolveRequestUri(updatedRequest);

                requests.add(HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(requestUri)
                        .headers(makeRequestHeaders(updatedRequest.getDevice()))
                        .body(mapper.encodeToBytes(updatedRequest))
                        .payload(updatedRequest)
                        .build());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<Imp> imps = httpCall.getRequest().getPayload().getImp();
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors, imps);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private String resolveRequestUri(BidRequest singleRequest) {
        return Optional.ofNullable(singleRequest.getSite())
                .map(site -> {
                    if (site.getId() == null || site.getId().isEmpty()) {
                        throw new PreBidException("request.Site.ID is not provided");
                    }
                    return String.format("%s?s=%s&prebid", endpointUrl, site.getId());
                })
                .or(() -> Optional.ofNullable(singleRequest.getApp())
                        .map(app -> {
                            if (app.getId() == null || app.getId().isEmpty()) {
                                throw new PreBidException("request.App.ID is not provided");
                            }
                            return String.format("%s?s=%s&prebid", endpointUrl, app.getId());
                        })
                )
                .orElseThrow(() -> new PreBidException("request.Site or request.App are not provided"));
    }

    private MultiMap makeRequestHeaders(Device device) {
        MultiMap headers = HttpUtil.headers();

        headers.set(HttpUtil.X_OPENRTB_VERSION_HEADER, X_OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));

        return headers;
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors, List<Imp> imps) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bidResponse.getCur(), imps, bid, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid resolveBidderBid(String currency, List<Imp> imps, Bid bid, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid.getImpid(), imps);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
        return BidderBid.of(bid, bidType, currency);
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else {
                    throw new PreBidException(String.format("Unsupported bidtype for bid: \"%s\"", impId));
                }
            }
        }
        throw new PreBidException("Failed to find impression \"%s\"".formatted(impId));
    }

}
