package org.prebid.server.auction.mediatypeprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.spring.config.bidder.model.MediaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MultiFormatMediaTypeProcessor implements MediaTypeProcessor {

    private static final String PREF_MTYPE_FIELD = "prefmtype";

    private final BidderCatalog bidderCatalog;

    public MultiFormatMediaTypeProcessor(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public MediaTypeProcessingResult process(BidRequest bidRequest,
                                             String bidderName,
                                             BidderAliases aliases,
                                             Account account) {
        final String resolvedBidderName = aliases.resolveBidder(bidderName);
        //todo: ext.prebid.biddercontrols clean-up should NOT be here
        // Suggestion: keep biddercontrols in the Auction Context
        // and clean it up on the extraction auction participants step
        final BidRequest.BidRequestBuilder bidRequestBuilder = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(prebid -> prebid.toBuilder().biddercontrols(null).build())
                .map(prebid -> {
                    final ExtRequest extRequest = ExtRequest.of(prebid);
                    extRequest.addProperties(bidRequest.getExt().getProperties());
                    return extRequest;
                })
                .map(extRequest -> bidRequest.toBuilder().ext(extRequest))
                .orElse(bidRequest.toBuilder());
        if (isMultiFormatSupported(resolvedBidderName)) {
            return MediaTypeProcessingResult.succeeded(bidRequestBuilder.build(), Collections.emptyList());
        }

        final MediaType preferredMediaType = preferredMediaType(bidRequest, account, bidderName, resolvedBidderName);
        if (preferredMediaType == null) {
            return MediaTypeProcessingResult.succeeded(bidRequestBuilder.build(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> updatedImps = bidRequest.getImp().stream()
                .map(imp -> processImp(imp, preferredMediaType, errors))
                .filter(Objects::nonNull)
                .toList();

        if (updatedImps.isEmpty()) {
            errors.add(BidderError.badInput("Bid request contains 0 impressions after filtering."));
            return MediaTypeProcessingResult.rejected(errors);
        }

        return MediaTypeProcessingResult.succeeded(bidRequestBuilder.imp(updatedImps).build(), errors);
    }

    private boolean isMultiFormatSupported(String bidder) {
        return bidderCatalog.bidderInfoByName(bidder).getOrtb().isMultiFormatSupported();
    }

    private MediaType preferredMediaType(BidRequest bidRequest,
                                         Account account,
                                         String originalBidderName,
                                         String resolvedBidderName) {

        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBiddercontrols)
                .map(bidders -> getBidder(originalBidderName, bidders))
                .map(bidder -> bidder.get(PREF_MTYPE_FIELD))
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .map(MediaType::of)
                .or(() -> Optional.ofNullable(account.getAuction())
                        .map(AccountAuctionConfig::getPreferredMediaTypes)
                        .map(preferredMediaTypes -> preferredMediaTypes.get(resolvedBidderName)))
                .orElse(null);
    }

    private static JsonNode getBidder(String bidderName, JsonNode biddersNode) {
        final Iterator<String> fieldNames = biddersNode.fieldNames();
        while (fieldNames.hasNext()) {
            final String fieldName = fieldNames.next();
            if (StringUtils.equalsIgnoreCase(bidderName, fieldName)) {
                return biddersNode.get(fieldName);
            }
        }
        return null;
    }

    private static Imp processImp(Imp imp, MediaType preferredMediaType, List<BidderError> errors) {
        if (!isMultiFormat(imp)) {
            return imp;
        }

        final Imp updatedImp = switch (preferredMediaType) {
            case BANNER -> imp.getBanner() != null
                    ? imp.toBuilder().video(null).audio(null).xNative(null).build()
                    : null;
            case VIDEO -> imp.getVideo() != null
                    ? imp.toBuilder().banner(null).audio(null).xNative(null).build()
                    : null;
            case AUDIO -> imp.getAudio() != null
                    ? imp.toBuilder().banner(null).video(null).xNative(null).build()
                    : null;
            case NATIVE -> imp.getXNative() != null
                    ? imp.toBuilder().banner(null).video(null).audio(null).build()
                    : null;
        };

        if (updatedImp == null) {
            errors.add(BidderError.badInput("Imp " + imp.getId() + " does not have a media type after filtering "
                    + "and has been removed from the request for this bidder."));
        }
        return updatedImp;
    }

    private static boolean isMultiFormat(Imp imp) {
        int count = 0;
        return (imp.getBanner() != null && ++count > 1)
                || (imp.getVideo() != null && ++count > 1)
                || (imp.getAudio() != null && ++count > 1)
                || (imp.getXNative() != null && ++count > 1);
    }
}
