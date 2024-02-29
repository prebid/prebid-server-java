package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;

import java.util.Objects;

public class BidRequestOrtbVersionConversionManager {

    private static final OrtbVersion MINIMAL_SUPPORTED_VERSION = OrtbVersion.ORTB_2_5;
    private static final OrtbVersion AUCTION_VERSION = OrtbVersion.ORTB_2_6;

    private final BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    public BidRequestOrtbVersionConversionManager(BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory) {
        this.ortbVersionConverterFactory = Objects.requireNonNull(ortbVersionConverterFactory);
    }

    public BidRequest convertToAuctionSupportedVersion(BidRequest bidRequest) {
        return ortbVersionConverterFactory
                .getConverter(MINIMAL_SUPPORTED_VERSION, AUCTION_VERSION)
                .convert(bidRequest);
    }

    public BidRequest convertFromAuctionSupportedVersion(BidRequest bidRequest, OrtbVersion ortbVersion) {
        return ortbVersionConverterFactory
                .getConverter(AUCTION_VERSION, ortbVersion)
                .convert(bidRequest);
    }
}
