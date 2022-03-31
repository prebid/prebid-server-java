package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.util.ObjectUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidderMediaTypeProcessor {

    private final BidderCatalog bidderCatalog;

    public BidderMediaTypeProcessor(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public BidRequest process(BidRequest bidRequest,
                              String supportedBidderName,
                              AuctionContext auctionContext) {

        final List<String> errors = auctionContext.getPrebidErrors();

        final Set<MediaType> supportedMediaTypes = extractSupportedMediaTypes(bidRequest, supportedBidderName);
        if (supportedMediaTypes.isEmpty()) {
            errors.add(supportedBidderName + " does not support any media types");
            return null;
        }

        return processBidRequest(
                bidRequest, supportedMediaTypes, errors, supportedBidderName);
    }

    private Set<MediaType> extractSupportedMediaTypes(BidRequest bidRequest, String supportedBidderName) {
        final BidderInfo.CapabilitiesInfo capabilitiesInfo = bidderCatalog
                .bidderInfoByName(supportedBidderName).getCapabilities();

        final List<MediaType> supportedMediaTypes;
        if (bidRequest.getSite() != null) {
            supportedMediaTypes = ObjectUtil.getIfNotNull(
                    capabilitiesInfo.getSite(), BidderInfo.PlatformInfo::getMediaTypes);
        } else {
            supportedMediaTypes = ObjectUtil.getIfNotNull(
                    capabilitiesInfo.getApp(), BidderInfo.PlatformInfo::getMediaTypes);
        }

        return CollectionUtils.isNotEmpty(supportedMediaTypes)
                ? EnumSet.copyOf(supportedMediaTypes)
                : EnumSet.noneOf(MediaType.class);
    }

    private BidRequest processBidRequest(BidRequest bidRequest,
                                         Set<MediaType> supportedMediaTypes,
                                         List<String> errors,
                                         String bidderName) {

        final List<Imp> modifiedImps = bidRequest.getImp().stream()
                .map(imp -> processImp(imp, supportedMediaTypes, errors, bidderName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (modifiedImps.isEmpty()) {
            errors.add("Bid request contains 0 impressions after filtering for " + bidderName);
            return null;
        }

        return bidRequest.toBuilder().imp(modifiedImps).build();
    }

    private static Imp processImp(Imp imp,
                                  Set<MediaType> supportedMediaTypes,
                                  List<String> errors,
                                  String bidderName) {

        final Set<MediaType> impMediaTypes = getMediaTypes(imp);
        final Set<MediaType> unsupportedMediaTypes = SetUtils.difference(impMediaTypes, supportedMediaTypes);

        if (unsupportedMediaTypes.isEmpty()) {
            return imp;
        }

        if (impMediaTypes.equals(unsupportedMediaTypes)) {
            errors.add("Imp " + imp.getId() + " does not have a supported media type for the " + bidderName
                    + "and has been removed from the request for this bidder");

            return null;
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        unsupportedMediaTypes.forEach(unsupportedMediaType -> removeMedia(impBuilder, unsupportedMediaType));

        return impBuilder.build();
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

    private static void removeMedia(Imp.ImpBuilder impBuilder, MediaType mediaType) {
        switch (mediaType) {
            case BANNER:
                impBuilder.banner(null);
                break;
            case VIDEO:
                impBuilder.video(null);
                break;
            case AUDIO:
                impBuilder.audio(null);
                break;
            case NATIVE:
                impBuilder.xNative(null);
                break;
            default:
                throw new IllegalArgumentException("Invalid media type");
        }
    }
}
