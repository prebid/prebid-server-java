package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.spring.config.bidder.model.MediaType;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link BidderMediaTypeProcessor} is an implementation of {@link MediaTypeProcessor} that
 * can be used to remove media types from {@link Imp} unsupported by specific bidder.
 */
public class BidderMediaTypeProcessor implements MediaTypeProcessor {

    private final BidderCatalog bidderCatalog;

    public BidderMediaTypeProcessor(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public MediaTypeProcessingResult process(BidRequest bidRequest, String supportedBidderName) {
        final Set<MediaType> supportedMediaTypes = extractSupportedMediaTypes(bidRequest, supportedBidderName);
        if (supportedMediaTypes.isEmpty()) {
            return MediaTypeProcessingResult.rejected(Collections.singletonList(
                    BidderError.badInput("Bidder does not support any media types.")));
        }

        final List<BidderError> errors = new ArrayList<>();
        final BidRequest modifiedBidRequest = processBidRequest(bidRequest, supportedMediaTypes, errors);

        return modifiedBidRequest != null
                ? MediaTypeProcessingResult.succeeded(modifiedBidRequest, errors)
                : MediaTypeProcessingResult.rejected(errors);
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
        final Set<MediaType> unsupportedMediaTypes = SetUtils.difference(impMediaTypes, supportedMediaTypes);

        if (unsupportedMediaTypes.isEmpty()) {
            return imp;
        }

        if (impMediaTypes.equals(unsupportedMediaTypes)) {
            errors.add(BidderError.badInput("Imp " + imp.getId() + " does not have a supported media type "
                    + "and has been removed from the request for this bidder."));

            return null;
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder();
        unsupportedMediaTypes.forEach(unsupportedMediaType -> removeMediaType(impBuilder, unsupportedMediaType));

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

    private static void removeMediaType(Imp.ImpBuilder impBuilder, MediaType mediaType) {
        switch (mediaType) {
            case BANNER -> impBuilder.banner(null);
            case VIDEO -> impBuilder.video(null);
            case AUDIO -> impBuilder.audio(null);
            case NATIVE -> impBuilder.xNative(null);
        }
    }
}
