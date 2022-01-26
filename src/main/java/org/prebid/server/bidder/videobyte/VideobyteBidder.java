package org.prebid.server.bidder.videobyte;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.videobyte.ExtImpVideobyte;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VideobyteBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_CURRENCY = "USD";
    private static final TypeReference<ExtPrebid<?, ExtImpVideobyte>> VIDEOBYTE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VideobyteBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpVideobyte extImpVideobyte = parseImpExt(imp);
                requests.add(createRequest(request, imp, extImpVideobyte));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return Result.of(requests, errors);
    }

    private ExtImpVideobyte parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), VIDEOBYTE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "Ignoring imp id=%s, error while decoding, err: %s", imp.getId(), e.getMessage()));
        }
    }

    private HttpRequest<BidRequest> createRequest(BidRequest bidRequest, Imp imp, ExtImpVideobyte extImpVideobyte) {
        final BidRequest modifiedBidRequest = bidRequest.toBuilder().imp(Collections.singletonList(imp)).build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(createUri(extImpVideobyte))
                .headers(headers(modifiedBidRequest))
                .body(mapper.encodeToBytes(modifiedBidRequest))
                .payload(modifiedBidRequest)
                .build();
    }

    private String createUri(ExtImpVideobyte extImpVideobyte) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(endpointUrl);
        } catch (URISyntaxException e) {
            throw new PreBidException(e.getMessage());
        }

        uriBuilder.addParameter("source", "pbs")
                .addParameter("pid", extImpVideobyte.getPublisherId());

        addUriParameterIfNotEmpty(uriBuilder, "placementId", extImpVideobyte.getPlacementId());
        addUriParameterIfNotEmpty(uriBuilder, "nid", extImpVideobyte.getNetworkId());

        return uriBuilder.toString();
    }

    private static void addUriParameterIfNotEmpty(URIBuilder uriBuilder, String parameter, String value) {
        if (StringUtils.isNotEmpty(value)) {
            uriBuilder.addParameter(parameter, value);
        }
    }

    private static MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers();

        final Site site = bidRequest.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ORIGIN_HEADER, site.getDomain());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getRef());
        }

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse("Bad server response."));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, resolveBidType(bid.getImpid(), bidRequest.getImp()), DEFAULT_CURRENCY))
                .collect(Collectors.toList());
    }

    private static BidType resolveBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (Objects.equals(impId, imp.getId())) {
                return imp.getBanner() != null ? BidType.banner : BidType.video;
            }
        }
        return BidType.video;
    }
}
