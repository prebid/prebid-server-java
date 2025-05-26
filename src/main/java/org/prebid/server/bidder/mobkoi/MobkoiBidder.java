package org.prebid.server.bidder.mobkoi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
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
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.mobkoi.ExtImpMobkoi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MobkoiBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMobkoi>> MOBKOI_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MobkoiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Imp firstImp = bidRequest.getImp().getFirst();

        final ExtImpMobkoi extImpMobkoi;
        final Imp modifiedFirstImp;
        try {
            extImpMobkoi = parseExtImp(firstImp);
            modifiedFirstImp = modifyImp(firstImp, extImpMobkoi);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String selectedEndpointUrl = resolveEndpoint(extImpMobkoi.getAdServerBaseUrl());

        return Result.withValue(BidderUtil.defaultRequest(
                modifyBidRequest(bidRequest, modifiedFirstImp),
                selectedEndpointUrl,
                mapper));
    }

    private ExtImpMobkoi parseExtImp(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), MOBKOI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "Invalid imp.ext for impression id %s. Error Information: %s"
                            .formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp modifyImp(Imp firstImp, ExtImpMobkoi extImpMobkoi) {
        if (StringUtils.isNotBlank(firstImp.getTagid())) {
            return firstImp;
        }

        if (StringUtils.isNotBlank(extImpMobkoi.getPlacementId())) {
            return firstImp.toBuilder().tagid(extImpMobkoi.getPlacementId()).build();
        }

        throw new PreBidException("invalid because it comes with neither request.imp[0].tagId nor "
                    + "req.imp[0].ext.Bidder.placementId");
    }

    // url is already validated with `bidder-params` json schema
    private String resolveEndpoint(String customUri) {
        if (customUri == null) {
            return endpointUrl;
        }
        try {
            final URI uri = new URI(customUri);
            return uri.resolve("/bid").toString();
        } catch (IllegalArgumentException | URISyntaxException e) {
            return endpointUrl;
        }
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, Imp modifiedFirstImp) {
        final User user = modifyUser(bidRequest.getUser());
        final List<Imp> imps = updateFirstImpWith(bidRequest.getImp(), modifiedFirstImp);
        return bidRequest.toBuilder().user(user).imp(imps).build();
    }

    private static User modifyUser(User user) {
        return Optional.ofNullable(user)
                .map(User::getConsent)
                .map(consent -> ExtUser.builder().consent(consent).build())
                .map(ext -> user.toBuilder().ext(ext).build())
                .orElse(user);
    }

    private static List<Imp> updateFirstImpWith(List<Imp> imps, Imp imp) {
        final List<Imp> modifiedImps = new ArrayList<>(imps);
        modifiedImps.set(0, imp);
        return Collections.unmodifiableList(modifiedImps);
    }

    @Override
    public final Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid()
                .stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.banner, "mobkoi", bidResponse.getCur()))
                .toList();
    }
}
