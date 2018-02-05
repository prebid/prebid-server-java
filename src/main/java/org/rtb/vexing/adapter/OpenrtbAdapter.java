package org.rtb.vexing.adapter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.apache.commons.collections4.CollectionUtils;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Basic {@link Adapter} implementation containing common logic functionality and helper methods.
 */
public abstract class OpenrtbAdapter implements Adapter {

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    // Params fields can be not in snake-case
    protected static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    protected static String validateUrl(String url) {
        Objects.requireNonNull(url);

        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("URL supplied is not valid: %s", url), e);
        }
    }

    protected static String encodeUrl(String format, Object... args) {
        Objects.requireNonNull(format);

        final String uri = String.format(format, args);
        try {
            return URLEncoder.encode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new PreBidException(String.format("Cannot encode uri: %s", uri));
        }
    }

    protected static Banner.BannerBuilder bannerBuilder(AdUnitBid adUnitBid) {
        return Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe);
    }

    protected static Video.VideoBuilder videoBuilder(AdUnitBid adUnitBid) {
        return Video.builder()
                .mimes(adUnitBid.video.mimes)
                .minduration(adUnitBid.video.minduration)
                .maxduration(adUnitBid.video.maxduration)
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .startdelay(adUnitBid.video.startdelay)
                .playbackmethod(Collections.singletonList(adUnitBid.video.playbackMethod))
                .protocols(adUnitBid.video.protocols);
    }

    protected static Site.SiteBuilder siteBuilder(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.preBidRequest.app != null ? null : Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer);
    }

    protected static Site makeSite(PreBidRequestContext preBidRequestContext) {
        final Site.SiteBuilder siteBuilder = siteBuilder(preBidRequestContext);
        return siteBuilder == null ? null : siteBuilder.build();
    }

    protected static Device.DeviceBuilder deviceBuilder(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.preBidRequest.device;

        // create a copy since device might be shared with other adapters
        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

        if (preBidRequestContext.preBidRequest.app == null) {
            deviceBuilder.ua(preBidRequestContext.ua);
        }

        return deviceBuilder
                .ip(preBidRequestContext.ip);
    }

    protected User.UserBuilder userBuilder(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.preBidRequest.app != null ? null : User.builder()
                .buyeruid(preBidRequestContext.uidsCookie.uidFrom(cookieFamily()))
                // id is a UID for "adnxs" (see logic in open-source implementation)
                .id(preBidRequestContext.uidsCookie.uidFrom("adnxs"));
    }

    protected User makeUser(PreBidRequestContext preBidRequestContext) {
        final User.UserBuilder userBuilder = userBuilder(preBidRequestContext);
        return userBuilder == null ? preBidRequestContext.preBidRequest.user : userBuilder.build();
    }

    protected static Source makeSource(PreBidRequestContext preBidRequestContext) {
        return Source.builder()
                .tid(preBidRequestContext.preBidRequest.tid)
                .fd(preBidRequestContext.preBidRequest.app == null ? 1 : null) // upstream, aka header
                .build();
    }

    protected static void validateAdUnitBidsMediaTypes(List<AdUnitBid> adUnitBids) {
        Objects.requireNonNull(adUnitBids);

        if (!adUnitBids.stream()
                .allMatch(adUnitBid -> adUnitBid.mediaTypes.stream()
                        .allMatch(mediaType -> isValidAdUnitBidVideoMediaType(mediaType, adUnitBid)))) {
            throw new PreBidException("Invalid AdUnit: VIDEO media type with no video data");
        }
    }

    private static boolean isValidAdUnitBidVideoMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        return !(MediaType.video.equals(mediaType)
                && (adUnitBid.video == null || CollectionUtils.isEmpty(adUnitBid.video.mimes)));
    }

    protected static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid, Set<MediaType> adapterAllowedMediaTypes) {
        Objects.requireNonNull(adUnitBid);
        Objects.requireNonNull(adapterAllowedMediaTypes);

        final Set<MediaType> allowedMediaTypes = new HashSet<>(adapterAllowedMediaTypes);
        allowedMediaTypes.retainAll(adUnitBid.mediaTypes);
        return allowedMediaTypes;
    }

    protected MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }

    protected static void validateImps(List<Imp> imps) {
        if (CollectionUtils.isEmpty(imps)) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
    }

    protected static AdUnitBid lookupBid(List<AdUnitBid> adUnitBids, String adUnitCode) {
        Objects.requireNonNull(adUnitBids);

        for (AdUnitBid adUnitBid : adUnitBids) {
            if (Objects.equals(adUnitBid.adUnitCode, adUnitCode)) {
                return adUnitBid;
            }
        }
        throw new PreBidException(String.format("Unknown ad unit code '%s'", adUnitCode));
    }

    @Override
    public boolean tolerateErrors() {
        return false; // by default all adapters throw up errors
    }

    /**
     * Extracts bids from response, returns empty stream in case of missing bid response or seat bids
     */
    protected static Stream<com.iab.openrtb.response.Bid> responseBidStream(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null ? Stream.empty()
                : bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream())
                .filter(Objects::nonNull);
    }
}
