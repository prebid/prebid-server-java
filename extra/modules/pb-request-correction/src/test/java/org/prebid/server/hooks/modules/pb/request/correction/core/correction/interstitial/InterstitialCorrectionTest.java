package org.prebid.server.hooks.modules.pb.request.correction.core.correction.interstitial;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class InterstitialCorrectionTest {

    private final InterstitialCorrection target = new InterstitialCorrection();

    @Test
    public void applyShouldCorrectInterstitial() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        Imp.builder().instl(0).build(),
                        Imp.builder().build(),
                        Imp.builder().instl(1).build()))
                .build();

        // when
        final BidRequest result = target.apply(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getInstl)
                .containsExactly(0, null, null);
    }
}
