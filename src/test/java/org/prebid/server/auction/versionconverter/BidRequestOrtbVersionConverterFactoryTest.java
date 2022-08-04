package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import org.junit.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestOrtbVersionConverterFactoryTest extends VertxTest {

    @Test
    public void createChainShouldReturnExpectedConverter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final BidRequestOrtbVersionConverter converter1 = request -> request.toBuilder()
                .app(App.builder().id("appId").build())
                .build();
        final BidRequestOrtbVersionConverter converter2 = request -> {
            assertThat(request.getApp()).isEqualTo(App.builder().id("appId").build());

            return request.toBuilder()
                    .app(request.getApp().toBuilder().domain("domain").build())
                    .build();
        };
        final BidRequestOrtbVersionConverter converter3 = request -> {
            assertThat(request.getApp()).isEqualTo(App.builder().id("appId").domain("domain").build());

            return request.toBuilder()
                    .app(request.getApp().toBuilder().bundle("bundle").build())
                    .build();
        };

        // when
        final BidRequestOrtbVersionConverter converter = BidRequestOrtbVersionConverterFactory
                .createChain(converter1, converter2, converter3);

        // and
        final BidRequest convertedBidRequest = converter.convert(bidRequest);

        // then
        assertThat(convertedBidRequest)
                .extracting(BidRequest::getApp)
                .isEqualTo(App.builder().id("appId").domain("domain").bundle("bundle").build());
    }
}
