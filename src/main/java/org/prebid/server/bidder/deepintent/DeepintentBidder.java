package org.prebid.server.bidder.deepintent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.deepintent.ExtImpDeepintent;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DeepintentBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpDeepintent>> DEEPINTENT_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String DISPLAY_MANAGER = "di_prebid";
    private static final String DISPLAY_MANAGER_VER = "2.0.0";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public DeepintentBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> modifiedImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                final Banner updatedBanner = buildImpBanner(imp.getBanner(), imp.getId());
                final ExtImpDeepintent extImpDeepintent = parseImpExt(imp);
                modifiedImps.add(modifyImp(imp, extImpDeepintent.getTagId(), updatedBanner));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<HttpRequest<BidRequest>> requests = modifiedImps.stream()
                .map(imp -> createRequest(request, imp))
                .toList();

        return Result.of(requests, errors);
    }

    private Banner buildImpBanner(Banner banner, String impId) {
        if (banner == null) {
            throw new PreBidException("We need a Banner Object in the request, imp : " + impId);
        }

        if (banner.getW() == null && banner.getH() == null) {
            final List<Format> bannerFormats = banner.getFormat();
            if (CollectionUtils.isEmpty(banner.getFormat())) {
                throw new PreBidException("At least one size is required, imp : " + impId);
            }
            final Format format = bannerFormats.getFirst();
            return banner.toBuilder().w(format.getW()).h(format.getH()).build();
        }

        return banner;
    }

    private ExtImpDeepintent parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), DEEPINTENT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Impression id=%s, has invalid Ext".formatted(imp.getId()));
        }
    }

    private Imp modifyImp(Imp imp, String tagId, Banner banner) {
        return imp.toBuilder()
                .banner(banner)
                .tagid(tagId)
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VER)
                .build();
    }

    private HttpRequest<BidRequest> createRequest(BidRequest request, Imp imp) {
        final BidRequest outgoingRequest = request.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(httpCall), Collections.emptyList());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidderCall<BidRequest> httpCall) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(httpCall.getRequest().getPayload(), bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .toList();
    }

    private BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return BidType.banner;
            }
        }
        throw new PreBidException("Failed to find impression with id: " + impId);
    }
}
