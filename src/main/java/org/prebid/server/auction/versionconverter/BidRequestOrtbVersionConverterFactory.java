package org.prebid.server.auction.versionconverter;

import org.prebid.server.auction.versionconverter.down.BidRequestOrtb26To25Converter;
import org.prebid.server.auction.versionconverter.up.BidRequestOrtb25To26Converter;
import org.prebid.server.json.JacksonMapper;

import java.util.Arrays;
import java.util.Map;

public class BidRequestOrtbVersionConverterFactory {

    private final Map<OrtbVersion, BidRequestOrtbVersionConverter> upConverters;
    private final Map<OrtbVersion, BidRequestOrtbVersionConverter> downConverters;

    public BidRequestOrtbVersionConverterFactory(JacksonMapper jacksonMapper) {
        upConverters = Map.of(
                OrtbVersion.ORTB_2_5, BidRequestOrtbVersionConverter.identity(),
                OrtbVersion.ORTB_2_6, createChain(new BidRequestOrtb25To26Converter()));

        downConverters = Map.of(
                OrtbVersion.ORTB_2_5, createChain(new BidRequestOrtb26To25Converter(jacksonMapper)),
                OrtbVersion.ORTB_2_6, BidRequestOrtbVersionConverter.identity());
    }

    static BidRequestOrtbVersionConverter createChain(BidRequestOrtbVersionConverter... converters) {
        return Arrays.stream(converters)
                .reduce(BidRequestOrtbVersionConverter::andThen)
                .orElseThrow();
    }

    public BidRequestOrtbVersionConverter getConverter(OrtbVersion fromVersion, OrtbVersion toVersion) {
        if (fromVersion.ordinal() <= toVersion.ordinal()) {
            return getConverterFrom(upConverters, toVersion);
        } else {
            return getConverterFrom(downConverters, toVersion);
        }
    }

    private static BidRequestOrtbVersionConverter getConverterFrom(
            Map<OrtbVersion, BidRequestOrtbVersionConverter> converters,
            OrtbVersion targetVersion) {

        final BidRequestOrtbVersionConverter converter = converters.get(targetVersion);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported OpenRTB version for conversion.");
        }

        return converter;
    }
}
