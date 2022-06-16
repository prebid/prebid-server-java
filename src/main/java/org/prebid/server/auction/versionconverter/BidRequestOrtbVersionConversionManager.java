package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Objects;

public class BidRequestOrtbVersionConversionManager {

    private static final OrtbVersion MINIMAL_SUPPORTED_VERSION = OrtbVersion.ORTB_2_5;
    private static final OrtbVersion AUCTION_VERSION = OrtbVersion.ORTB_2_6;

    private final BidderCatalog bidderCatalog;
    private final BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    public BidRequestOrtbVersionConversionManager(BidderCatalog bidderCatalog,
                                                  BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory) {

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.ortbVersionConverterFactory = Objects.requireNonNull(ortbVersionConverterFactory);
    }

    public BidRequest convertToAuctionSupportedVersion(BidRequest bidRequest) {
        return ortbVersionConverterFactory
                .getConverter(MINIMAL_SUPPORTED_VERSION, AUCTION_VERSION)
                .convert(bidRequest);
    }

    public BidRequest convertToBidderSupportedVersion(BidRequest bidRequest, String bidderName) {
        return ortbVersionConverterFactory
                .getConverter(AUCTION_VERSION, bidderCatalog.bidderInfoByName(bidderName).getOrtbVersion())
                .convert(bidRequest);
    }
}
