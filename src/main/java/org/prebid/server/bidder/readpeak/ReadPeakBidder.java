package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ReadPeakBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpReadPeak>> READPEAK_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<ExtPrebid<ExtBidPrebid, ObjectNode>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String PRICE_MACRO = "${AUCTION_PRICE}";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ReadPeakBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            final ExtImpReadPeak extImpReadPeak;
            try {
                extImpReadPeak = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp, extImpReadPeak);
                httpRequests.add(makeHttpRequest(request, modifiedImp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (httpRequests.isEmpty()) {
            return Result.withError(BidderError
                    .badInput(String.format("Failed to find compatible impressions for request %s", request.getId())));

        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpReadPeak parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), READPEAK_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize ReadPeak extension: " + e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpReadPeak extImpReadPeak) {
        return imp.toBuilder()
                .bidfloor(extImpReadPeak.getBidFloor() != null ? extImpReadPeak.getBidFloor() : imp.getBidfloor())
                .tagid(StringUtils.isNotBlank(extImpReadPeak.getTagId()) ? extImpReadPeak.getTagId() : null)
                .build();
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request, Imp imp) {
        final BidRequest.BidRequestBuilder requestBuilder = request.toBuilder()
                .imp(List.of(imp));

        final ExtImpReadPeak extImpReadPeak = parseImpExt(imp);

        final Publisher publisher = Publisher.builder()
                .id(extImpReadPeak.getPublisherId())
                .build();

        if (request.getSite() != null) {
            final Site site = request.getSite().toBuilder()
                    .id(StringUtils.isNotBlank(extImpReadPeak.getSiteId())
                            ? extImpReadPeak.getSiteId() : request.getSite().getId())
                    .publisher(publisher)
                    .build();
            requestBuilder.site(site);
        } else if (request.getApp() != null) {
            final App app = request.getApp().toBuilder()
                    .id(StringUtils.isNotBlank(extImpReadPeak.getSiteId())
                            ? extImpReadPeak.getSiteId() : request.getApp().getId())
                    .publisher(publisher)
                    .build();
            requestBuilder.app(app);
        }

        final BidRequest outgoingRequest = requestBuilder.build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderBid> bids = extractBids(bidResponse);
            return Result.withValues(bids);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(bid -> makeBid(bid, bidResponse))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid makeBid(Bid bid, BidResponse bidResponse) {
        if (bid.getExt() == null) {
            throw new PreBidException("Bid ext is null");
        }
        final Bid resolvedBid = resolveMacros(bid);
        if (resolvedBid.getExt() == null) {
            throw new PreBidException("Bid ext is null");
        }
        final BidType bidType = getBidType(resolvedBid);

        // Parse existing ExtBidPrebidMeta
        final ExtBidPrebid extBidPrebid = parseExtBidPrebidMeta(resolvedBid);
        final ExtBidPrebidMeta meta = extBidPrebid.getMeta();

        // Validate rendererName and rendererVersion
        if (StringUtils.isBlank(meta.getRendererName())) {
            throw new PreBidException("RendererName should not be empty");
        }
        if (StringUtils.isBlank(meta.getRendererVersion())) {
            throw new PreBidException("RendererVersion should not be empty");
        }

        // Build modified ExtBidPrebidMeta
        final ExtBidPrebidMeta modifiedMeta = ExtBidPrebidMeta.builder()
                .rendererName(meta.getRendererName())
                .rendererVersion(meta.getRendererVersion())
                .advertiserDomains(meta.getAdvertiserDomains()) // add other required fields here
                .build();

        // Build modified ExtBidPrebid
        final ExtBidPrebid modifiedPrebid = extBidPrebid.toBuilder()
                .meta(modifiedMeta)
                .build();

        // Convert to ObjectNode
        final ObjectNode modifiedBidExt = mapper.mapper().valueToTree(ExtPrebid.of(modifiedPrebid, null));

        // Return BidderBid
        return BidderBid.of(resolvedBid.toBuilder().ext(modifiedBidExt).build(), bidType, bidResponse.getCur());
    }

    private ExtBidPrebid parseExtBidPrebidMeta(Bid bid) {
        try {
            if (bid.getExt() == null) {
                throw new PreBidException("Bid ext is null");
            }
            final ExtBidPrebid extPrebid = mapper.mapper()
                    .convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE).getPrebid();
            if (extPrebid == null) {
                throw new PreBidException("Failed to parse extPrebid from bid ext");
            }
            return extPrebid;
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Bid resolveMacros(Bid bid) {
        final BigDecimal price = bid.getPrice();
        final String priceAsString = price != null ? price.toPlainString() : "0";

        return bid.toBuilder()
                .nurl(StringUtils.replace(bid.getNurl(), PRICE_MACRO, priceAsString))
                .adm(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .burl(StringUtils.replace(bid.getAdm(), PRICE_MACRO, priceAsString))
                .build();
    }

    private BidType getBidType(Bid bid) {
        final Integer markupType = ObjectUtils.defaultIfNull(bid.getMtype(), 0);

        return switch (markupType) {
            case 1 -> BidType.banner;
            case 4 -> BidType.xNative;
            default -> throw new PreBidException(
                    "Unable to fetch mediaType " + bid.getMtype() + " in multi-format: " + bid.getImpid());
        };
    }

    private Bid addBidMeta(Bid bid) {
        // Build the ExtBidPrebidMeta object
        final ExtBidPrebidMeta extBidPrebidMeta = ExtBidPrebidMeta.builder()
                .advertiserDomains(bid.getAdomain())
                .build();

        // Convert ExtBidPrebidMeta to ObjectNode
        final ObjectNode bidExt = bid.getExt() != null ? bid.getExt().deepCopy()
                : JsonNodeFactory.instance.objectNode();
        bidExt.set("prebid", mapper.mapper().valueToTree(extBidPrebidMeta));

        return bid.toBuilder()
                .ext(bidExt)
                .build();
    }
}
