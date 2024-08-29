package org.prebid.server.hooks.modules.response.correction.core.correction.appvideohtml;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Site;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.response.correction.core.config.model.AppVideoHtmlConfig;
import org.prebid.server.hooks.modules.response.correction.core.config.model.Config;
import org.prebid.server.json.ObjectMapperProvider;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AppVideoHtmlCorrectionProducerTest {

    private final AppVideoHtmlCorrection CORRECTION_INSTANCE =
            new AppVideoHtmlCorrection(ObjectMapperProvider.mapper(), 0.1);

    private final AppVideoHtmlCorrectionProducer target = new AppVideoHtmlCorrectionProducer(CORRECTION_INSTANCE);

    @Test
    public void produceShouldReturnCorrectionInstance() {
        // when & then
        assertThat(target.produce()).isSameAs(CORRECTION_INSTANCE);
    }

    @Test
    public void shouldProduceReturnFalseWhenAppVideoHtmlConfigIsDisabled() {
        // given
        final Config givenConfig = givenConfig(false);
        final BidRequest givenRequest = BidRequest.builder().app(App.builder().build()).build();

        // when & then
        assertThat(target.shouldProduce(givenConfig, givenRequest)).isFalse();
    }

    @Test
    public void shouldProduceReturnFalseWhenBidRequestIsNotAppRequest() {
        // given
        final Config givenConfig = givenConfig(true);
        final BidRequest givenRequest = BidRequest.builder().site(Site.builder().build()).build();

        // when
        target.shouldProduce(givenConfig, givenRequest);

        // when & then
        assertThat(target.shouldProduce(givenConfig, givenRequest)).isFalse();
    }

    @Test
    public void shouldProduceReturnTrueWhenConfigIsEnabledAndBidRequestHasApp() {
        // given
        final Config givenConfig = givenConfig(true);
        final BidRequest givenRequest = BidRequest.builder().app(App.builder().build()).build();

        // when
        target.shouldProduce(givenConfig, givenRequest);

        // when & then
        assertThat(target.shouldProduce(givenConfig, givenRequest)).isTrue();
    }

    private static Config givenConfig(boolean enabled) {
        return Config.of(true, AppVideoHtmlConfig.of(enabled, null));
    }

}
