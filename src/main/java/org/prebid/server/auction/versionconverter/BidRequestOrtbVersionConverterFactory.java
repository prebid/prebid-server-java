package org.prebid.server.auction.versionconverter;

import org.prebid.server.auction.versionconverter.down.BidRequestOrtb26To25Converter;
import org.prebid.server.auction.versionconverter.up.BidRequestOrtb25To26Converter;

import java.util.Arrays;
import java.util.Map;

public class BidRequestOrtbVersionConverterFactory {

    private final Map<OrtbVersion, BidRequestOrtbVersionConverter> bidRequestOrtbConverters;

    public BidRequestOrtbVersionConverterFactory() {
        bidRequestOrtbConverters = Map.of(
                OrtbVersion.ORTB_2_5, createChain(new BidRequestOrtb26To25Converter()),
                OrtbVersion.ORTB_2_6, createChain(new BidRequestOrtb25To26Converter()));
    }

    static BidRequestOrtbVersionConverter createChain(BidRequestOrtbVersionConverter... converters) {
        return Arrays.stream(converters).reduce(
                BidRequestOrtbVersionConverter.identity(),
                BidRequestOrtbVersionConverter::andThen);
    }

    public BidRequestOrtbVersionConverter getConverter(OrtbVersion targetVersion) {
        final BidRequestOrtbVersionConverter converter = bidRequestOrtbConverters.get(targetVersion);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported OpenRTB version for conversion.");
        }

        return converter;
    }
}
