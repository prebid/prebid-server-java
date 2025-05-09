package org.prebid.server.bidder.mobkoi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
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
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.mobkoi.ExtImpMobkoi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.stream.Collectors;

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

        final Imp firstImp = bidRequest.getImp().stream().findFirst()
                .orElseThrow(() -> new PreBidException("No impression found"));

        final ExtImpMobkoi extImpMobkoi;
        try {
            extImpMobkoi = parseExtImp(firstImp);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final Imp validImp;
        if (firstImp.getTagid() == null) {
            if (extImpMobkoi.getPlacementId() != null) {
                validImp = modifyImp(firstImp, extImpMobkoi.getPlacementId());
            } else {
                return Result.withError(
                        BidderError.badInput(
                                "invalid because it comes with neither request.imp[0].tagId nor "
                                        + "req.imp[0].ext.Bidder.placementId"));
            }
        } else {
            validImp = firstImp;
        }

        List<Imp> modifiedImps = bidRequest.getImp();
        if (validImp != firstImp) {
            modifiedImps = updateFirstImpWith(bidRequest.getImp(), validImp);
        }

        final String selectedEndpointUrl = Optional.ofNullable(extImpMobkoi.getAdServerBaseUrl())
                .flatMap(this::validateAndReplaceUri)
                .orElse(endpointUrl);

        final User user = modifyUser(bidRequest.getUser());

        return Result.withValue(BidderUtil.defaultRequest(
                modifyBidRequest(bidRequest, user, modifiedImps),
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

    private Optional<String> validateAndReplaceUri(String customUri) {
        try {
            HttpUtil.validateUrl(customUri);
            final URI uri = new URI(customUri);
            return Optional.of(uri.resolve("/bid").toString());
        } catch (IllegalArgumentException | URISyntaxException e) {
            return Optional.empty();
        }
    }

    private Imp modifyImp(Imp imp, String tagId) {
        return imp.toBuilder().tagid(tagId).build();
    }

    private List<Imp> updateFirstImpWith(List<Imp> imp, Imp validImp) {
        final List<Imp> imps = new Vector<>(imp);
        imps.set(0, validImp);
        return Collections.unmodifiableList(imps);
    }

    private User modifyUser(User user) {
        if (user == null || user.getConsent() == null) {
            return user;
        }

        final String consent = user.getConsent();
        final ExtUser userExt = ExtUser.builder().consent(consent).build();

        return user.toBuilder().ext(userExt).build();
    }

    private static BidRequest modifyBidRequest(BidRequest bidRequest, User user, List<Imp> imps) {
        return bidRequest.toBuilder().user(user).imp(imps).build();
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
                .collect(Collectors.toList());
    }
}
