package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Source;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConverterFactory.LATEST_SUPPORTED_ORTB_VERSION;

public class BidRequestOrtbVersionConverterFactoryTest extends VertxTest {

    private BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    @Before
    public void setUp() {
        ortbVersionConverterFactory = new BidRequestOrtbVersionConverterFactory();
    }

    @Test
    public void getConverterForInternalUseShouldReturnExpectedConverter() {
        // when
        final BidRequestOrtbVersionConverter converter = ortbVersionConverterFactory.getConverterForInternalUse();

        // then
        assertThat(converter)
                .extracting(BidRequestOrtbVersionConverter::outVersion)
                .isEqualTo(LATEST_SUPPORTED_ORTB_VERSION);
    }

    @Test
    public void createChainShouldThrowExceptionIfConvertersInChainAreIncompatible() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> BidRequestOrtbVersionConverterFactory.createChain(
                BidRequestOrtbVersionConverterFactory.BidRequestOrtbCustomConverter.of(
                        OrtbVersion.ORTB_2_5, OrtbVersion.ORTB_2_6, identity()),
                BidRequestOrtbVersionConverterFactory.BidRequestOrtbCustomConverter.of(
                        OrtbVersion.ORTB_2_5, OrtbVersion.ORTB_2_6, identity())));
    }

    @Test
    public void createChainShouldReturnExpectedConverter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final BidRequestOrtbVersionConverter converter1 =
                BidRequestOrtbVersionConverterFactory.BidRequestOrtbCustomConverter.of(
                        OrtbVersion.ORTB_2_5,
                        OrtbVersion.ORTB_2_6,
                        request -> request.toBuilder().app(App.builder().id("appId").build()).build());
        final BidRequestOrtbVersionConverter converter2 =
                BidRequestOrtbVersionConverterFactory.BidRequestOrtbCustomConverter.of(
                        OrtbVersion.ORTB_2_6,
                        OrtbVersion.ORTB_2_5,
                        request -> request.toBuilder().source(Source.builder().tid("sourceId").build()).build());

        // when
        final BidRequestOrtbVersionConverter converter = BidRequestOrtbVersionConverterFactory
                .createChain(converter1, converter2);

        // and
        final BidRequest convertedBidRequest = converter.convert(bidRequest);

        // then
        assertThat(converter)
                .extracting(BidRequestOrtbVersionConverter::inVersion, BidRequestOrtbVersionConverter::outVersion)
                .containsExactly(OrtbVersion.ORTB_2_5, OrtbVersion.ORTB_2_5);

        // and
        assertThat(convertedBidRequest)
                .extracting(BidRequest::getApp, BidRequest::getSource)
                .containsExactly(App.builder().id("appId").build(), Source.builder().tid("sourceId").build());
    }
}
