package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Source;
import org.junit.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestOrtbVersionConverterFactoryTest extends VertxTest {

    @Test
    public void createChainShouldReturnExpectedConverter() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final BidRequestOrtbVersionConverter converter1 =
                request -> request.toBuilder().app(App.builder().id("appId").build()).build();
        final BidRequestOrtbVersionConverter converter2 =
                request -> request.toBuilder().source(Source.builder().tid("sourceId").build()).build();

        // when
        final BidRequestOrtbVersionConverter converter = BidRequestOrtbVersionConverterFactory
                .createChain(converter1, converter2);

        // and
        final BidRequest convertedBidRequest = converter.convert(bidRequest);

        // then
        assertThat(convertedBidRequest)
                .extracting(BidRequest::getApp, BidRequest::getSource)
                .containsExactly(App.builder().id("appId").build(), Source.builder().tid("sourceId").build());
    }
}
