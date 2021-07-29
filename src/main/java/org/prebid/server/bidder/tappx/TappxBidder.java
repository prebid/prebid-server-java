package org.prebid.server.bidder.tappx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
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
import org.prebid.server.proto.openrtb.ext.request.tappx.ExtImpTappx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TappxBidder implements Bidder<BidRequest> {

    private static final String VERSION = "1.2";
    private static final String TYPE_CNN = "prebid";

    private static final TypeReference<ExtPrebid<?, ExtImpTappx>> TAPX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTappx>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TappxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final ExtImpTappx extImpTappx;
        final String url;
        try {
            extImpTappx = parseBidRequestToExtImpTappx(request);
            url = resolveUrl(extImpTappx, request.getTest());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BigDecimal extBidfloor = extImpTappx.getBidfloor();
        final BidRequest outgoingRequest = extBidfloor != null && extBidfloor.signum() > 0
                ? modifyRequest(request, extBidfloor)
                : request;

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(url)
                .body(mapper.encode(outgoingRequest))
                .payload(outgoingRequest)
                .build());
    }

    /**
     * Retrieves first {@link ExtImpTappx} from {@link Imp}.
     */
    private ExtImpTappx parseBidRequestToExtImpTappx(BidRequest request) {
        try {
            return mapper.mapper().convertValue(request.getImp().get(0).getExt(), TAPX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    /**
     * Builds endpoint url based on adapter-specific pub settings from imp.ext.
     */
    private String resolveUrl(ExtImpTappx extImpTappx, Integer test) {
        final String host = extImpTappx.getHost();
        if (StringUtils.isBlank(host)) {
            throw new PreBidException("Tappx host undefined");
        }

        final String endpoint = extImpTappx.getEndpoint();
        if (StringUtils.isBlank(endpoint)) {
            throw new PreBidException("Tappx endpoint undefined");
        }

        final String tappxkey = extImpTappx.getTappxkey();
        if (StringUtils.isBlank(tappxkey)) {
            throw new PreBidException("Tappx tappxkey undefined");
        }

        return buildUrl(host, endpoint, tappxkey, test);
    }

    private String buildUrl(String host, String endpoint, String tappxkey, Integer test) {
        try {
            final String baseUri = resolveBaseUri(host);
            final URIBuilder uriBuilder = new URIBuilder(baseUri);

            if (!StringUtils.containsIgnoreCase(host, endpoint)) {
                final List<String> pathSegments = new ArrayList<>();
                uriBuilder.getPathSegments().stream()
                        .filter(StringUtils::isNotBlank)
                        .forEach(pathSegments::add);
                pathSegments.add(StringUtils.strip(endpoint, "/"));
                uriBuilder.setPathSegments(pathSegments);
            }

            uriBuilder.addParameter("tappxkey", tappxkey);
            uriBuilder.addParameter("v", VERSION);
            uriBuilder.addParameter("type_cnn", TYPE_CNN);

            if (test != null && test == 0) {
                final String ts = String.valueOf(System.nanoTime());
                uriBuilder.addParameter("ts", ts);
            }
            return uriBuilder.build().toString();
        } catch (URISyntaxException e) {
            throw new PreBidException(String.format("Failed to build endpoint URL: %s", e.getMessage()));
        }
    }

    private String resolveBaseUri(String host) {
        return StringUtils.startsWithAny(host.toLowerCase(), "http://", "https://")
                ? host
                : endpointUrl + host;
    }

    /**
     * Modify request's first imp.
     */
    private static BidRequest modifyRequest(BidRequest request, BigDecimal extBidfloor) {
        final Imp modifiedFirstImp = request.getImp().get(0).toBuilder().bidfloor(extBidfloor).build();
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp());
        modifiedImps.set(0, modifiedFirstImp);

        return request.toBuilder().imp(modifiedImps).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }
}
