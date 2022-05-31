package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import org.prebid.server.auction.versionconverter.down.BidRequestOrtb26To25Converter;
import org.prebid.server.auction.versionconverter.up.BidRequestOrtb25To26Converter;

import java.util.Map;
import java.util.function.UnaryOperator;

public class BidRequestOrtbVersionConverterFactory {

    private static final OrtbVersion LATEST_SUPPORTED_ORTB_VERSION = OrtbVersion.ORTB_2_6;

    private final Map<OrtbVersion, BidRequestOrtbVersionConverter> bidRequestOrtbConverters;

    public BidRequestOrtbVersionConverterFactory() {
        bidRequestOrtbConverters = Map.of(
                OrtbVersion.ORTB_2_5, createChain(new BidRequestOrtb26To25Converter()),
                OrtbVersion.ORTB_2_6, createChain(new BidRequestOrtb25To26Converter()));
    }

    private static BidRequestOrtbVersionConverter createChain(BidRequestOrtbVersionConverter... converters) {
        validateChain(converters);

        return BidRequestOrtbCustomConverter.of(
                converters[0].inVersion(),
                converters[converters.length - 1].outVersion(),
                bidRequest -> {
                    BidRequest transitionalBidRequest = bidRequest;
                    for (BidRequestOrtbVersionConverter converter : converters) {
                        transitionalBidRequest = converter.convert(transitionalBidRequest);
                    }

                    return transitionalBidRequest;
                });
    }

    private static void validateChain(BidRequestOrtbVersionConverter... converters) {
        for (int i = 0; i < converters.length - 1; i++) {
            if (converters[i].outVersion() != converters[i + 1].inVersion()) {
                throw new IllegalArgumentException("Chain of OpenRTB version converters must be monotonic.");
            }
        }
    }

    public BidRequestOrtbVersionConverter getConverterForInternalUse() {
        return getConverter(LATEST_SUPPORTED_ORTB_VERSION);
    }

    public BidRequestOrtbVersionConverter getConverter(OrtbVersion targetVersion) {
        final BidRequestOrtbVersionConverter converter = bidRequestOrtbConverters.get(targetVersion);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported OpenRTB version for conversion.");
        }

        return converter;
    }

    @AllArgsConstructor(staticName = "of")
    private static class BidRequestOrtbCustomConverter implements BidRequestOrtbVersionConverter {

        private final OrtbVersion inVersion;

        private final OrtbVersion outVersion;

        private final UnaryOperator<BidRequest> converter;

        @Override
        public OrtbVersion inVersion() {
            return inVersion;
        }

        @Override
        public OrtbVersion outVersion() {
            return outVersion;
        }

        @Override
        public BidRequest convert(BidRequest bidRequest) {
            return converter.apply(bidRequest);
        }
    }
}
