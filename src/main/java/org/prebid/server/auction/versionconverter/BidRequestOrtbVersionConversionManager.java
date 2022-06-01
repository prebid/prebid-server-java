package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Objects;

public class BidRequestOrtbVersionConversionManager {

    private static final OrtbVersion AUCTION_ORTB_VERSION = OrtbVersion.ORTB_2_6;

    private final BidderCatalog bidderCatalog;
    private final BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    public BidRequestOrtbVersionConversionManager(BidderCatalog bidderCatalog,
                                                  BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.ortbVersionConverterFactory = Objects.requireNonNull(ortbVersionConverterFactory);
    }

    public BidRequest convertToAuctionSupportedVersion(BidRequest bidRequest) {
        return ortbVersionConverterFactory.getConverter(AUCTION_ORTB_VERSION).convert(bidRequest);
    }

    public BidRequest convertToBidderSupportedVersion(BidRequest bidRequest, String bidderName) {
        return ortbVersionConverterFactory.getConverter(
                        bidderCatalog.bidderInfoByName(bidderName).getOrtbVersion())
                .convert(bidRequest);
    }
}
