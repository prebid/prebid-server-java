package org.prebid.server.bidder.amx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.amx.model.AmxBidExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.amx.ExtImpAmx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AmxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpAmx>> AMX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String ADAPTER_VERSION = "pbs1.2";
    private static final String VERSION_PARAM = "v";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AmxBidder(String endpointUrl, JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
        this.endpointUrl = resolveEndpointUrl(endpointUrl);
    }

    private static String resolveEndpointUrl(String url) {
        final URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(HttpUtil.validateUrl(Objects.requireNonNull(url)));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid url: %s, error: %s".formatted(url, e.getMessage()));
        }
        return uriBuilder
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
                final Imp modifiedImp = StringUtils.isNotBlank(adUnitId)
                        ? imp.toBuilder().tagid(adUnitId).build()
                        : imp;
                modifiedImps.add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final BidRequest outgoingRequest = createOutgoingRequest(request, publisherId, modifiedImps);

        return Result.of(Collections.singletonList(
                        BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper)),
                errors);
    }

    private ExtImpAmx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), AMX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidRequest createOutgoingRequest(BidRequest request, String publisherId, List<Imp> imps) {
        final BidRequest.BidRequestBuilder outgoingRequest = request.toBuilder();

        if (StringUtils.isNotBlank(publisherId)) {
            final App app = request.getApp();
            if (app != null) {
                outgoingRequest
                        .app(app.toBuilder()
                                .publisher(resolvePublisher(app.getPublisher(), publisherId))
                                .build());
            }

            final Site site = request.getSite();
            if (site != null) {
                outgoingRequest
                        .site(site.toBuilder()
                                .publisher(resolvePublisher(site.getPublisher(), publisherId))
                                .build());
            }
        }

        return outgoingRequest.imp(imps).build();
    }

    private Publisher resolvePublisher(Publisher publisher, String publisherId) {
        return publisher != null
                ? publisher.toBuilder().id(publisherId).build()
                : Publisher.builder().id(publisherId).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
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
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid createBidderBid(Bid bid, String cur, List<BidderError> errors) {
        final AmxBidExt amxBidExt;
        try {
            amxBidExt = parseBidderExt(bid.getExt());
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
        // TODO: After adding support to change seat data, add bid.ext bidderCode processing
        return BidderBid.of(resolveBid(bid, amxBidExt.getDemandSource()), getBidType(amxBidExt), cur);
    }

    private AmxBidExt parseBidderExt(ObjectNode ext) {
        if (ext == null || ext.isEmpty()) {
            return AmxBidExt.empty();
        }

        try {
            return mapper.mapper().convertValue(ext, AmxBidExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private BidType getBidType(AmxBidExt amxBidExt) {
        if (amxBidExt.getStartDelay() != null) {
            return BidType.video;
        } else if (amxBidExt.getCreativeType() != null && amxBidExt.getCreativeType() == 10) {
            return BidType.xNative;
        } else {
            return BidType.banner;
        }
    }

    private Bid resolveBid(Bid bid, String demandSource) {
        final List<String> aDomains = bid.getAdomain();
        if (CollectionUtils.isEmpty(aDomains) && StringUtils.isBlank(demandSource)) {
            return bid;
        }

        return bid.toBuilder().ext(resolveBidExt(demandSource, aDomains, bid.getExt())).build();
    }

    private ObjectNode resolveBidExt(String demandSource, List<String> aDomains, ObjectNode bidExt) {
        final ObjectNode bidExtUpdated = bidExt != null && !bidExt.isMissingNode()
                ? bidExt
                : mapper.mapper().createObjectNode();
        final JsonNode bidExtPrebid = resolveBidExtPrebid(demandSource, aDomains, bidExtUpdated.get("prebid"));

        return bidExtUpdated.set("prebid", bidExtPrebid);
    }

    private ObjectNode resolveBidExtPrebid(String demandSource, List<String> aDomains, JsonNode bidExtPrebid) {
        final ExtBidPrebidMeta extBidPrebidMeta = ExtBidPrebidMeta.builder()
                .demandSource(demandSource)
                .advertiserDomains(aDomains)
                .build();
        if (bidExtPrebid == null || bidExtPrebid.isMissingNode()) {
            return mapper.mapper().valueToTree(ExtBidPrebid.builder().meta(extBidPrebidMeta).build());
        }

        final ObjectNode bidExtPrebidCasted = (ObjectNode) bidExtPrebid;
        return bidExtPrebidCasted.set("meta", mapper.mapper().valueToTree(extBidPrebidMeta));
    }
}
