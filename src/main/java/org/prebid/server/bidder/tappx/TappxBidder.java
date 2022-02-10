package org.prebid.server.bidder.tappx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
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
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TappxBidder implements Bidder<BidRequest> {

    private static final String VERSION = "1.4";
    private static final String TYPE_CNN = "prebid";

    private static final TypeReference<ExtPrebid<?, ExtImpTappx>> TAPX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final Pattern NEW_ENDPOINT_PATTERN = Pattern.compile("^(zz|vz)[0-9]{3,}([a-z]{2}|test)$");
    private static final String SUBDOMAIN_MACRO = "{{subdomain}}";

    private final String endpointUrl;
    private final Clock clock;
    private final JacksonMapper mapper;

    public TappxBidder(String endpointUrl, Clock clock, JacksonMapper mapper) {
        this.endpointUrl = Objects.requireNonNull(endpointUrl);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> imps = request.getImp();

        final ExtImpTappx extImpTappx;
        final String url;
        try {
            extImpTappx = parseImpExt(imps.get(0));
            url = resolveUrl(extImpTappx, request.getTest());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest modifiedRequest = modifyRequest(request, modifyImps(imps, extImpTappx), extImpTappx);
        return Result.withValue(makeHttpRequest(modifiedRequest, url));
    }

    private ExtImpTappx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TAPX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static List<Imp> modifyImps(List<Imp> imps, ExtImpTappx extImpTappx) {
        List<Imp> modifiedImps = new ArrayList<>(imps);
        modifiedImps.set(0, modifyImp(imps.get(0), extImpTappx));

        return modifiedImps;
    }

    private String resolveUrl(ExtImpTappx extImpTappx, Integer test) {
        final String subdomain = extImpTappx.getEndpoint();
        final boolean isNewEndpoint = NEW_ENDPOINT_PATTERN.matcher(subdomain).matches();

        final String baseUri = isNewEndpoint ? resolveNewHost(subdomain) : resolveOldHost();
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(baseUri);
        } catch (URISyntaxException e) {
            throw new PreBidException(String.format("Failed to build endpoint URL: %s", e.getMessage()));
        }

        if (!isNewEndpoint) {
            final List<String> pathSegments = uriBuilder.getPathSegments();
            uriBuilder.setPathSegments(ListUtils.union(pathSegments, Collections.singletonList(subdomain)));
        }

        uriBuilder.addParameter("tappxkey", extImpTappx.getTappxkey());
        uriBuilder.addParameter("v", VERSION);
        uriBuilder.addParameter("type_cnn", TYPE_CNN);

        if (test != null && test == 0) {
            uriBuilder.addParameter("ts", String.valueOf(clock.millis()));
        }

        return uriBuilder.toString();
    }

    private String resolveNewHost(String subdomain) {
        return endpointUrl.replace(SUBDOMAIN_MACRO, subdomain + ".pub") + "/rtb/";
    }

    private String resolveOldHost() {
        return endpointUrl.replace(SUBDOMAIN_MACRO, "ssp.api") + "/rtb/v2";
    }

    private static Imp modifyImp(Imp imp, ExtImpTappx extImpTappx) {
        final BigDecimal extBidFloor = extImpTappx.getBidfloor();

        return extBidFloor != null && extBidFloor.signum() == 1
                ? imp.toBuilder().bidfloor(extBidFloor).build()
                : imp;
    }

    private BidRequest modifyRequest(BidRequest request, List<Imp> imps, ExtImpTappx extImpTappx) {
        return request.toBuilder()
                .imp(imps)
                .ext(createRequestExt(extImpTappx))
                .build();
    }

    private ExtRequest createRequestExt(ExtImpTappx extImpTappx) {
        final TappxBidderExt tappxBidderExt = TappxBidderExt.builder()
                .tappxkey(extImpTappx.getTappxkey())
                .mktag(extImpTappx.getMktag())
                .bcid(extImpTappx.getBcid())
                .bcrid(extImpTappx.getBcrid())
                .build();

        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("bidder", mapper.mapper().valueToTree(tappxBidderExt));
        return extRequest;
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, String endpointUrl) {
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .uri(endpointUrl)
                .body(mapper.encodeToBytes(request))
                .payload(request)
                .build();
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
