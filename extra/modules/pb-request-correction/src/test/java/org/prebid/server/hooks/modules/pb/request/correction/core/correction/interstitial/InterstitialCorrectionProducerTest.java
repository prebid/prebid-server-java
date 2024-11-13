package org.prebid.server.hooks.modules.pb.request.correction.core.correction.interstitial;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.pb.request.correction.core.config.model.Config;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class InterstitialCorrectionProducerTest {

    private final InterstitialCorrectionProducer target = new InterstitialCorrectionProducer();

    @Test
    public void shouldProduceReturnsFalseIfCorrectionDisabled() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(false)
                .build();
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldProduceReturnsFalseIfThereIsNothingToDo() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(true)
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .app(App.builder().build())
                .build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldProduceReturnsFalseIfSourceIsNotPrebidMobile() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(true)
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .app(App.builder().ext(ExtApp.of(ExtAppPrebid.of("source", null), null)).build())
                .build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldProduceReturnsFalseIfBundleNotAnAndroid() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(true)
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .app(App.builder()
                        .bundle("bundle")
                        .ext(ExtApp.of(ExtAppPrebid.of("prebid-mobile", null), null))
                        .build())
                .build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldProduceReturnsFalseIfVersionInvalid() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(true)
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .app(App.builder()
                        .bundle("bundleAndroid")
                        .ext(ExtApp.of(ExtAppPrebid.of("prebid-mobile", "1a.2.3"), null))
                        .build())
                .build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldProduceReturnsTrueWhenAllConditionsMatch() {
        // given
        final Config config = Config.builder()
                .interstitialCorrectionEnabled(true)
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().instl(1).build()))
                .app(App.builder()
                        .bundle("bundleAndroid")
                        .ext(ExtApp.of(ExtAppPrebid.of("prebid-mobile", "1.2.3"), null))
                        .build())
                .build();

        // when
        final boolean result = target.shouldProduce(config, bidRequest);

        // then
        assertThat(result).isTrue();
    }
}
