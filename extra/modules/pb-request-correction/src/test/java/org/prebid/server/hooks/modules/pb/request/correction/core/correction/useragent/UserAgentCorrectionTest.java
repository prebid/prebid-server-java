package org.prebid.server.hooks.modules.pb.request.correction.core.correction.useragent;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserAgentCorrectionTest {

    private final UserAgentCorrection target = new UserAgentCorrection();

    @Test
    public void applyShouldCorrectUserAgent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("blah PrebidMobile/1asdf blah").build())
                .build();

        // when
        final BidRequest result = target.apply(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getDevice)
                .extracting(Device::getUa)
                .isEqualTo("blah  blah");
    }
}
