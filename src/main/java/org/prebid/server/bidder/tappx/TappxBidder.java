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
import org.prebid.server.bidder.tappx.model.TappxBidderExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TappxBidder implements Bidder<BidRequest> {

    private static final String VERSION = "1.4";
    private static final String TYPE_CNN = "prebid";

    private static final TypeReference<ExtPrebid<?, ExtImpTappx>> TAPX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
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
        final BidRequest outgoingRequest = modifyRequest(request, extImpTappx);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(url)
                .body(mapper.encodeToBytes(outgoingRequest))
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
        final String subdomen = extImpTappx.getSubdomen();
        if (StringUtils.isBlank(subdomen)) {
            throw new PreBidException("Tappx endpoint undefined");
        }

        final String tappxkey = extImpTappx.getTappxkey();
        if (StringUtils.isBlank(tappxkey)) {
            throw new PreBidException("Tappx tappxkey undefined");
        }

        final boolean isMatcherEndpoint = isMatcherEndpoint(subdomen);
        final String host = resolvedHost(subdomen, isMatcherEndpoint);

        return buildUrl(host, subdomen, tappxkey, test, isMatcherEndpoint);
    }

    private String resolvedHost(String subdomen, boolean isMatcherEndpoint) {
        if (isMatcherEndpoint) {
            return subdomen.replace("{{subdomen}}", subdomen + ".pub") + "/rtb/";
        }
        return subdomen.replace("{{subdomen}}", "ssp.api") + "/rtb/v2";
    }

    private String buildUrl(String host, String subdomen, String tappxkey, Integer test, Boolean isMatcherEndpoint) {
        try {
            final String baseUri = resolveBaseUri(host);
            final URIBuilder uriBuilder = new URIBuilder(baseUri);

            if (!isMatcherEndpoint) {
                final List<String> pathSegments = new ArrayList<>();
                uriBuilder.getPathSegments().stream()
                        .filter(StringUtils::isNotBlank)
                        .forEach(pathSegments::add);
                pathSegments.add(StringUtils.strip(subdomen, "/"));
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

    private boolean isMatcherEndpoint(String endpointUrl) {
        final Pattern versionPattern = Pattern.compile("^(zz|vz)[0-9]{3,}([a-z]{2}|test)$");
        final Matcher versionMatcher = versionPattern.matcher(endpointUrl);
        return versionMatcher.matches();
    }

    /**
     * Modify request's first imp.
     */
    private BidRequest modifyRequest(BidRequest request, ExtImpTappx extImpTappx) {
        final List<Imp> modifiedImps = new ArrayList<>(request.getImp());
        final BigDecimal extBidfloor = extImpTappx.getBidfloor();
        if (extBidfloor != null && extBidfloor.signum() > 0) {
            final Imp modifiedFirstImp = request.getImp().get(0).toBuilder().bidfloor(extBidfloor).build();
            modifiedImps.set(0, modifiedFirstImp);
        }

        return request.toBuilder().imp(modifiedImps).ext(getExtRequest(extImpTappx)).build();
    }

    private ExtRequest getExtRequest(ExtImpTappx extImpTappx) {
        final ExtRequest extRequest = ExtRequest.empty();
        final TappxBidderExt tappxBidderExt = TappxBidderExt.of(extImpTappx.getTappxkey(), extImpTappx.getMktag(),
                extImpTappx.getBcid(), extImpTappx.getBcrid());
        extRequest.addProperty("bidder", mapper.mapper().valueToTree(tappxBidderExt));

        return extRequest;
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
