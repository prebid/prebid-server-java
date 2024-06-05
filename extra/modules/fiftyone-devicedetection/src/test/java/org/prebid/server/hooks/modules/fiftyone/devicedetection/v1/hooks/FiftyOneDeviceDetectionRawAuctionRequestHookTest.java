package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.FiftyOneDeviceDetectionModule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class FiftyOneDeviceDetectionRawAuctionRequestHookTest {
    @Test
    public void codeShouldStartWithModuleCode() throws Exception {
        // given
        final RawAuctionRequestHook hook = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                mock(DeviceEnricher.class)
        );

        // when and then
        assertThat(hook.code()).startsWith(FiftyOneDeviceDetectionModule.CODE);
    }
}
