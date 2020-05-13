package org.prebid.server.bidder.nanointeractive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.nanointeractive.ExtImpNanointeractive;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.math.BigDecimal;

public class NanointeractiveBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNanointeractive>> NANOINTERACTIVE_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpNanointeractive>>() {
            };
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NanointeractiveBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        String reference = "";
        for (Imp imp : request.getImp()) {
            try {
                final Imp validImp = validateImp(imp);
                final ExtImpNanointeractive extImp = parseImpExt(imp);
                if (StringUtils.isBlank(extImp.getPid())) {
                    throw new PreBidException("pid is empty");
                }

                if (StringUtils.isBlank(reference) && StringUtils.isNotBlank(extImp.getRef())) {
                    reference = extImp.getRef();
                }
                validImps.add(validImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(validImps)
                .site(modified(reference, request.getSite()))
                .build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers(request))
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private Imp validateImp(Imp imp) {
        if (imp.getBanner() == null) {
            throw new PreBidException("invalid MediaType. NanoInteractive only supports Banner type.");
        }
        return imp;
    }

    private ExtImpNanointeractive parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), NANOINTERACTIVE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Site modified(String reference, Site site) {
        if (StringUtils.isNotBlank(reference)) {
            return site == null ? Site.builder().ref(reference).build() : site.toBuilder().ref(reference).build();
        }
        return site;
    }

    private MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers().add("x-openrtb-version", "2.5");
        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
        }

        final Site site = bidRequest.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        if (bidRequest.getUser() != null && StringUtils.isNotBlank(bidRequest.getUser().getBuyeruid())) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.COOKIE_HEADER, String.format("Nano=%s",
                    bidRequest.getUser().getBuyeruid().trim()));
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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
                .filter(bid -> bid.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
