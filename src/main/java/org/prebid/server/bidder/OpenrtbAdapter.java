package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.MediaType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Basic {@link Adapter} implementation containing common logic functionality and helper methods.
 */
public abstract class OpenrtbAdapter implements Adapter<BidRequest, BidResponse> {

    protected final String cookieFamilyName;

    protected OpenrtbAdapter(String cookieFamilyName) {
        this.cookieFamilyName = Objects.requireNonNull(cookieFamilyName);
    }

    @Override
    public boolean tolerateErrors() {
        return false; // by default all adapters throw up errors
    }

    @Override
    public TypeReference<BidResponse> responseTypeReference() {
        return new TypeReference<BidResponse>() {
        };
    }

    protected static Banner.BannerBuilder bannerBuilder(AdUnitBid adUnitBid) {
        final List<Format> sizes = adUnitBid.getSizes();
        return Banner.builder()
                .w(sizes.get(0).getW())
                .h(sizes.get(0).getH())
                .format(sizes)
                .topframe(adUnitBid.getTopframe());
    }

    protected static Video.VideoBuilder videoBuilder(AdUnitBid adUnitBid) {
        final org.prebid.server.proto.request.Video video = adUnitBid.getVideo();
        final Format format = adUnitBid.getSizes().get(0);
        return Video.builder()
                .mimes(video.getMimes())
                .minduration(video.getMinduration())
                .maxduration(video.getMaxduration())
                .w(format.getW())
                .h(format.getH())
                .startdelay(video.getStartdelay())
                .playbackmethod(Collections.singletonList(video.getPlaybackMethod()))
                .protocols(video.getProtocols());
    }

    protected static Site.SiteBuilder siteBuilder(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.getPreBidRequest().getApp() != null ? null : Site.builder()
                .domain(preBidRequestContext.getDomain())
                .page(preBidRequestContext.getReferer());
    }

    protected static Site makeSite(PreBidRequestContext preBidRequestContext) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder.build();
    }

    protected static Device.DeviceBuilder deviceBuilder(PreBidRequestContext preBidRequestContext) {
        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        final Device device = preBidRequest.getDevice();

        // create a copy since device might be shared with other adapters
        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

        if (preBidRequest.getApp() == null) {
            deviceBuilder.ua(preBidRequestContext.getUa());
        }

        return deviceBuilder.ip(preBidRequestContext.getIp());
    }

    protected User.UserBuilder userBuilder(PreBidRequestContext preBidRequestContext) {
        final UidsCookie uidsCookie = preBidRequestContext.getUidsCookie();
        final User user = preBidRequestContext.getPreBidRequest().getUser();
        final ExtUser userExt = user != null ? user.getExt() : null;
        return preBidRequestContext.getPreBidRequest().getApp() != null ? null : User.builder()
                .buyeruid(uidsCookie.uidFrom(cookieFamilyName))
                // id is a UID for "adnxs" (see logic in open-source implementation)
                .id(uidsCookie.uidFrom("adnxs"))
                .ext(userExt);
    }

    protected User makeUser(PreBidRequestContext preBidRequestContext) {
        final User.UserBuilder userBuilder = userBuilder(preBidRequestContext);
        return userBuilder == null ? preBidRequestContext.getPreBidRequest().getUser() : userBuilder.build();
    }

    protected static Source makeSource(PreBidRequestContext preBidRequestContext) {
        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        return Source.builder()
                .tid(preBidRequest.getTid())
                .fd(preBidRequest.getApp() == null ? 1 : null) // upstream, aka header
                .build();
    }

    protected static void validateAdUnitBidsMediaTypes(List<AdUnitBid> adUnitBids, Set<MediaType> allowedMediaTypes) {
        if (!adUnitBids.stream()
                .allMatch(adUnitBid -> adUnitBid.getMediaTypes().stream()
                        .filter(allowedMediaTypes::contains)
                        .allMatch(mediaType -> isValidAdUnitBidVideoMediaType(mediaType, adUnitBid)))) {
            throw new PreBidException("Invalid AdUnit: VIDEO media type with no video data");
        }
    }

    private static boolean isValidAdUnitBidVideoMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        final org.prebid.server.proto.request.Video video = adUnitBid.getVideo();
        return !(MediaType.video.equals(mediaType)
                && (video == null || CollectionUtils.isEmpty(video.getMimes())));
    }

    protected static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid, Set<MediaType> adapterAllowedMediaTypes) {
        final Set<MediaType> allowedMediaTypes = new HashSet<>(adapterAllowedMediaTypes);
        allowedMediaTypes.retainAll(adUnitBid.getMediaTypes());
        return allowedMediaTypes;
    }

    protected static void validateImps(List<Imp> imps) {
        if (CollectionUtils.isEmpty(imps)) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
    }

    protected static AdUnitBid lookupBid(List<AdUnitBid> adUnitBids, String adUnitCode) {
        for (AdUnitBid adUnitBid : adUnitBids) {
            if (Objects.equals(adUnitBid.getAdUnitCode(), adUnitCode)) {
                return adUnitBid;
            }
        }
        throw new PreBidException(String.format("Unknown ad unit code '%s'", adUnitCode));
    }

    /**
     * Extracts bids from response, returns empty stream in case of missing bid response or seat bids
     */
    protected static Stream<com.iab.openrtb.response.Bid> responseBidStream(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Stream.empty()
                : bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream())
                .filter(Objects::nonNull);
    }
}
