package org.prebid.server.bidder.amx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.amx.model.AmxBidExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.amx.ExtImpAmx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AMX {@link Bidder} implementation.
 */
public class AmxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAmx>> AMX_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAmx>>() {
            };

    private static final String ADAPTER_VERSION = "pbs1.0";
    private static final String VERSION_PARAM = "v";
    private static final String VAST_SEARCH_POINT = "</Impression>";
    private static final String VAST_IMPRESSION_FORMAT = "<Impression><![CDATA[%s]]></Impression>";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AmxBidder(String endpointUrl, JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.endpointUrl = new URIBuilder()
                .setPath(HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl)))
                .addParameter(VERSION_PARAM, ADAPTER_VERSION)
                .toString();
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        String publisherId = null;
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpAmx extImpAmx = parseImpExt(imp);
                final String tagId = extImpAmx.getTagId();
                if (StringUtils.isNotBlank(tagId)) {
                    publisherId = tagId;
                }

                final String adUnitId = extImpAmx.getAdUnitId();
                if (StringUtils.isNotBlank(adUnitId)) {
                    modifiedImps.add(imp.toBuilder().tagid(adUnitId).build());
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = createOutgoingRequest(request, publisherId, modifiedImps);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers())
                        .payload(outgoingRequest)
                        .body(mapper.encode(outgoingRequest))
                        .build()), errors);
    }

    private ExtImpAmx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), AMX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest createOutgoingRequest(BidRequest request, String publisherId, List<Imp> imps) {
        return updateRequestIfPublisherIdPresent(request, publisherId).toBuilder()
                .imp(imps)
                .build();
    }

    private BidRequest updateRequestIfPublisherIdPresent(BidRequest request, String publisherId) {
        return StringUtils.isBlank(publisherId)
                ? request
                : updateRequestWithPublisherId(request, publisherId);
    }

    private BidRequest updateRequestWithPublisherId(BidRequest request, String publisherId) {
        final BidRequest.BidRequestBuilder modifiedRequest = request.toBuilder();

        final App app = request.getApp();
        if (app != null) {
            modifiedRequest
                    .app(app.toBuilder()
                            .publisher(resolvePublisher(app.getPublisher(), publisherId))
                            .build());
        }

        final Site site = request.getSite();
        if (site != null) {
            modifiedRequest
                    .site(site.toBuilder()
                            .publisher(resolvePublisher(site.getPublisher(), publisherId))
                            .build());
        }

        return modifiedRequest.build();
    }

    private Publisher resolvePublisher(Publisher publisher, String publisherId) {
        return publisher != null
                ? publisher.toBuilder().id(publisherId).build()
                : Publisher.builder().id(publisherId).build();
    }

    private MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, List<BidderError> errors) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, bidResponse.getCur(), errors))
                .collect(Collectors.toList());
    }

    private BidderBid createBidderBid(Bid bid, String cur, List<BidderError> errors) {
        AmxBidExt amxBidExt = null;
        try {
            amxBidExt = parseBidderExt(bid.getExt());
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
        }

        final BidType bidType = getMediaType(amxBidExt);

        return BidderBid.of(bidType == BidType.video ? updateVideoBid(bid, amxBidExt) : bid, bidType, cur);
    }

    private AmxBidExt parseBidderExt(ObjectNode ext) {
        if (ext == null || StringUtils.isBlank(ext.toPrettyString())) {
            return AmxBidExt.of(null, null);
        }

        try {
            return mapper.mapper().convertValue(ext, AmxBidExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getMediaType(AmxBidExt bidExt) {
        return StringUtils.isNotBlank(bidExt.getStartDelay())
                ? BidType.video
                : BidType.banner;
    }

    private static Bid updateVideoBid(Bid bid, AmxBidExt bidExt) {

        return bid.toBuilder()
                .nurl("")
                .adm(updateAdm(bidExt, bid.getNurl(), bid.getAdm(), bid.getId()))
                .build();
    }

    private static void validateAdm(String adm, String bidId) {
        if (StringUtils.isBlank(adm)) {
            throw new PreBidException(String.format("Adm should not be blank in bidder: %s", bidId));
        }

        if (!adm.contains(VAST_SEARCH_POINT)) {
            throw new PreBidException(String.format("Adm should contain vast search point in bidder: %s", bidId));
        }
    }

    private static String updateAdm(AmxBidExt bidExt, String nurl, String adm, String bidId) {
        final StringBuilder updatedAdm = new StringBuilder();
        validateAdm(adm, bidId);

        int lastInd = adm.lastIndexOf(VAST_SEARCH_POINT);

        updatedAdm.append(adm, 0, lastInd + VAST_SEARCH_POINT.length());
        addValueIfNotEmpty(nurl, updatedAdm);

        for (String himp : bidExt.getHimp()) {
            addValueIfNotEmpty(himp, updatedAdm);
        }

        return updatedAdm
                .append(adm.substring(lastInd + VAST_SEARCH_POINT.length()))
                .toString();
    }

    private static void addValueIfNotEmpty(String value, StringBuilder dest) {
        if (StringUtils.isNotBlank(value)) {
            dest.append(String.format(VAST_IMPRESSION_FORMAT, value));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}

