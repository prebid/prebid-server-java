package org.prebid.server.auction.bidderrequestpostprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.Result;
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
import java.util.stream.Collectors;

public class BidderRequestPreferredMediaProcessor implements BidderRequestPostProcessor {

    private static final String PREF_MTYPE_FIELD = "prefmtype";

    private final BidderCatalog bidderCatalog;

    public BidderRequestPreferredMediaProcessor(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        final String bidderName = bidderRequest.getBidder();
        final BidRequest bidRequest = bidderRequest.getBidRequest();

        final String resolvedBidderName = aliases.resolveBidder(bidderName);
        if (isMultiFormatSupported(resolvedBidderName)) {
            return noAction(bidderRequest);
        }

        final Optional<MediaType> preferredMediaType = preferredMediaType(bidRequest, bidderName)
                .or(() -> preferredMediaType(auctionContext.getAccount(), resolvedBidderName));
        if (preferredMediaType.isEmpty()) {
            return noAction(bidderRequest);
        }

        final List<BidderError> errors = new ArrayList<>();
        final BidRequest modifiedBidRequest = processBidRequest(bidRequest, preferredMediaType.get(), errors);

        return modifiedBidRequest != null
                ? Future.succeededFuture(Result.of(bidderRequest.with(modifiedBidRequest), errors))
                : Future.failedFuture(new BidderRequestRejectedException(
                BidRejectionReason.REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE, errors));
    }

    private static Future<Result<BidderRequest>> noAction(BidderRequest bidderRequest) {
        return Future.succeededFuture(Result.of(bidderRequest, Collections.emptyList()));
    }

    private boolean isMultiFormatSupported(String bidder) {
        return bidderCatalog.bidderInfoByName(bidder).getOrtb().isMultiFormatSupported();
    }

    private static Optional<MediaType> preferredMediaType(BidRequest bidRequest, String bidderName) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBiddercontrols)
                .map(bidders -> getBidder(bidderName, bidders))
                .map(bidder -> bidder.get(PREF_MTYPE_FIELD))
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .map(MediaType::of);
    }

    private static Optional<MediaType> preferredMediaType(Account account, String bidderName) {
        return Optional.ofNullable(account.getAuction())
                .map(AccountAuctionConfig::getPreferredMediaTypes)
                .map(preferredMediaTypes -> preferredMediaTypes.get(bidderName));
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

    private static BidRequest processBidRequest(BidRequest bidRequest,
                                                MediaType preferredMediaType,
                                                List<BidderError> errors) {

        final List<Imp> modifiedImps = bidRequest.getImp().stream()
                .map(imp -> processImp(imp, preferredMediaType, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("Bid request contains 0 impressions after filtering."));
            return null;
        }

        return bidRequest.toBuilder().imp(modifiedImps).build();
    }

    private static Imp processImp(Imp imp, MediaType preferredMediaType, List<BidderError> errors) {
        if (!isMultiFormat(imp)) {
            return imp;
        }

        final Banner banner = preferredMediaType == MediaType.BANNER ? imp.getBanner() : null;
        final Video video = preferredMediaType == MediaType.VIDEO ? imp.getVideo() : null;
        final Audio audio = preferredMediaType == MediaType.AUDIO ? imp.getAudio() : null;
        final Native xNative = preferredMediaType == MediaType.NATIVE ? imp.getXNative() : null;

        if (ObjectUtils.allNull(banner, video, audio, xNative)) {
            errors.add(BidderError.badInput("""
                    Imp %s does not have a media type after filtering \
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

    private static boolean isMultiFormat(Imp imp) {
        int count = 0;
        return (imp.getBanner() != null && ++count > 1)
                || (imp.getVideo() != null && ++count > 1)
                || (imp.getAudio() != null && ++count > 1)
                || (imp.getXNative() != null && ++count > 1);
    }
}
