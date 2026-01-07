package org.prebid.server.auction.bidderrequestpostprocessor;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidderRequestMediaFilter implements BidderRequestPostProcessor {

    private static final EnumSet<MediaType> NONE_OF_MEDIA_TYPES = EnumSet.noneOf(MediaType.class);

    private final BidderCatalog bidderCatalog;

    public BidderRequestMediaFilter(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        final String resolvedBidderName = aliases.resolveBidder(bidderRequest.getBidder());
        final BidRequest bidRequest = bidderRequest.getBidRequest();
        final Set<MediaType> supportedMediaTypes = extractSupportedMediaTypes(bidRequest, resolvedBidderName);
        if (supportedMediaTypes.isEmpty()) {
            return rejected(Collections.singletonList(
                    BidderError.badInput("Bidder does not support any media types.")));
        }

        final List<BidderError> errors = new ArrayList<>();
        final BidRequest modifiedBidRequest = processBidRequest(bidRequest, supportedMediaTypes, errors);

        return modifiedBidRequest != null
                ? Future.succeededFuture(Result.of(bidderRequest.with(modifiedBidRequest), errors))
                : rejected(errors);
    }

    private static Future<Result<BidderRequest>> rejected(List<BidderError> errors) {
        return Future.failedFuture(
                new BidderRequestRejectedException(BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE, errors));
    }

    private Set<MediaType> extractSupportedMediaTypes(BidRequest bidRequest, String bidderName) {
        final BidderInfo.CapabilitiesInfo capabilitiesInfo = bidderCatalog
                .bidderInfoByName(bidderName)
                .getCapabilities();

        final BidderInfo.PlatformInfo site = bidRequest.getSite() != null ? capabilitiesInfo.getSite() : null;
        final BidderInfo.PlatformInfo app = bidRequest.getApp() != null ? capabilitiesInfo.getApp() : null;
        final BidderInfo.PlatformInfo dooh = bidRequest.getDooh() != null ? capabilitiesInfo.getDooh() : null;

        return Stream.of(site, app, dooh)
                .filter(Objects::nonNull)
                .findFirst()
                .map(BidderInfo.PlatformInfo::getMediaTypes)
                .filter(mediaTypes -> !mediaTypes.isEmpty())
                .map(EnumSet::copyOf)
                .orElse(NONE_OF_MEDIA_TYPES);
    }

    private static BidRequest processBidRequest(BidRequest bidRequest,
                                                Set<MediaType> supportedMediaTypes,
                                                List<BidderError> errors) {

        final List<Imp> modifiedImps = bidRequest.getImp().stream()
                .map(imp -> processImp(imp, supportedMediaTypes, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("Bid request contains 0 impressions after filtering."));
            return null;
        }

        return bidRequest.toBuilder().imp(modifiedImps).build();
    }

    private static Imp processImp(Imp imp, Set<MediaType> supportedMediaTypes, List<BidderError> errors) {
        final Set<MediaType> impMediaTypes = getMediaTypes(imp);
        if (supportedMediaTypes.containsAll(impMediaTypes)) {
            return imp;
        }

        final Banner banner = supportedMediaTypes.contains(MediaType.BANNER) ? imp.getBanner() : null;
        final Video video = supportedMediaTypes.contains(MediaType.VIDEO) ? imp.getVideo() : null;
        final Audio audio = supportedMediaTypes.contains(MediaType.AUDIO) ? imp.getAudio() : null;
        final Native xNative = supportedMediaTypes.contains(MediaType.NATIVE) ? imp.getXNative() : null;

        if (ObjectUtils.allNull(banner, video, audio, xNative)) {
            errors.add(BidderError.badInput("""
                    Imp %s does not have a supported media type \
                    and has been removed from the request for this bidder.""".formatted(imp.getId())));

            return null;
        }

        return imp.toBuilder()
                .banner(banner)
                .video(video)
                .audio(audio)
                .xNative(xNative)
                .build();
    }

    private static Set<MediaType> getMediaTypes(Imp imp) {
        return Stream.of(
                        imp.getBanner() != null ? MediaType.BANNER : null,
                        imp.getVideo() != null ? MediaType.VIDEO : null,
                        imp.getAudio() != null ? MediaType.AUDIO : null,
                        imp.getXNative() != null ? MediaType.NATIVE : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MediaType.class)));
    }
}
