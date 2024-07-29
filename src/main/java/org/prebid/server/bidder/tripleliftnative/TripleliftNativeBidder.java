package org.prebid.server.bidder.tripleliftnative;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TripleliftNativeBidder implements Bidder<BidRequest> {

    private static final String UNKNOWN_PUBLISHER_ID = "unknown";

    private static final TypeReference<ExtPrebid<?, ExtImpTriplelift>> TRIPLELIFT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String MSN_DOMAIN = "msn.com";

    private final String endpointUrl;
    private final List<String> publisherWhiteList;
    private final JacksonMapper mapper;

    public TripleliftNativeBidder(String endpointUrl, List<String> publisherWhiteList, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.publisherWhiteList = Objects.requireNonNull(publisherWhiteList);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            try {
                validImps.add(modifyImp(imp, bidRequest.getSite()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final String publisherId = effectivePublisherId(bidRequest);
        if (!publisherWhiteList.contains(publisherId)) {
            errors.add(BidderError.badInput("Unsupported publisher for triplelift_native"));
            return Result.of(Collections.emptyList(), errors);
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions for triplelift"));
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest updatedRequest = bidRequest.toBuilder()
                .imp(validImps)
                .build();

        return Result.of(Collections.singletonList(BidderUtil.defaultRequest(updatedRequest, endpointUrl, mapper)),
                errors);
    }

    private Imp modifyImp(Imp imp, Site site) throws PreBidException {
        if (imp.getXNative() == null) {
            throw new PreBidException("no native object specified");
        }

        final TripleliftNativeExtImp impExt = parseExtImp(imp);
        final ExtImpTriplelift impExtBidder = impExt.getBidder();

        return imp.toBuilder()
                .tagid(resolveTagId(site, impExtBidder, impExt.getData()))
                .bidfloor(impExtBidder.getFloor())
                .build();
    }

    private TripleliftNativeExtImp parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TripleliftNativeExtImp.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private String resolveTagId(Site site, ExtImpTriplelift bidder, TripleliftNativeExtImpData data) {
        final String inventoryCode = bidder.getInventoryCode();
        if (StringUtils.isBlank(inventoryCode)) {
            throw new PreBidException("no inv_code specified");
        }

        final String tagCode = Optional.ofNullable(data).map(TripleliftNativeExtImpData::getTagCode).orElse(null);
        if (StringUtils.isBlank(tagCode)) {
            return inventoryCode;
        }

        final String domain = Optional.ofNullable(site)
                .map(Site::getPublisher)
                .map(Publisher::getDomain)
                .orElse(null);

        return Objects.equals(domain, MSN_DOMAIN) ? tagCode : inventoryCode;
    }

    private String effectivePublisherId(BidRequest bidRequest) {
        final Publisher publisher = findPublisher(bidRequest);
        if (publisher == null) {
            return UNKNOWN_PUBLISHER_ID;
        }
        final String id = publisher.getId();

        final ExtPublisher publisherExt = publisher.getExt();
        final ExtPublisherPrebid extPublisherPrebid = publisherExt != null ? publisherExt.getPrebid() : null;
        if (extPublisherPrebid != null && StringUtils.isNotBlank(extPublisherPrebid.getParentAccount())) {
            return extPublisherPrebid.getParentAccount();
        }

        return StringUtils.isBlank(id) ? UNKNOWN_PUBLISHER_ID : id;
    }

    private static Publisher findPublisher(BidRequest bidRequest) {
        final App app = bidRequest.getApp();
        if (app != null) {
            return app.getPublisher();
        }

        final Site site = bidRequest.getSite();
        if (site != null) {
            return site.getPublisher();
        }

        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.xNative, bidResponse.getCur()))
                .toList();
    }
}
