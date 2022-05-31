package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.versionconverter.down.BidRequestOrtb26To25Converter;
import org.prebid.server.auction.versionconverter.up.BidRequestOrtb25To26Converter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestOrtbVersionConvertersTest extends VertxTest {

    private final Map<BidRequestOrtbVersionConverter, BidRequestOrtbVersionConverter> mutuallyInverseConverters =
            Map.of(new BidRequestOrtb25To26Converter(), new BidRequestOrtb26To25Converter());

    @Test
    public void useOfMutuallyInverseConvertersShouldNotAffectBidRequest() {
        assertThat(mutuallyInverseConverters).allSatisfy((converter, inverseConverter) -> {
            // given
            final BidRequest bidRequest = givenDefaultBidRequest();

            // when
            final BidRequest result = BidRequestOrtbVersionConverterFactory
                    .createChain(converter, inverseConverter)
                    .convert(bidRequest);

            // then
            assertThat(result).isEqualTo(bidRequest);
        });
    }

    @Test
    public void multipleUseOfSameConverterShouldNotModifyRequestAfterFirstInteraction() {
        final List<BidRequestOrtbVersionConverter> converters =
                mutuallyInverseConverters.entrySet().stream()
                        .map(entry -> List.of(entry.getKey(), entry.getValue()))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());

        assertThat(converters).allSatisfy(converter -> {
            // given
            final BidRequest bidRequest = givenDefaultBidRequest();

            // when
            final BidRequest transitionResult = converter.convert(bidRequest);
            final BidRequest result = converter.convert(transitionResult);

            // then
            assertThat(result).isEqualTo(transitionResult);
        });
    }

    private static BidRequest givenDefaultBidRequest() {
        // TODO: add fields that will be moved during conversion from 2.5 to 2.6.
        return BidRequest.builder().build();
    }
}
