package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BidRequestReaderTest {
    private static Function<BidRequest, CollectedEvidence> buildHook() throws Exception {
        final RawAuctionRequestHook hook = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                mock(DeviceEnricher.class)
        );
        final AuctionRequestPayload payload = mock(AuctionRequestPayload.class);
        final AuctionInvocationContext auctionInvocationContext = mock(AuctionInvocationContext.class);
        return bidRequest -> {
            when(payload.bidRequest()).thenReturn(bidRequest);
            return ((ModuleContext) hook.call(payload, auctionInvocationContext)
                    .result()
                    .moduleContext())
                    .collectedEvidence();
        };
    }

    @Test
    public void shouldNotFailOnNoDevice() throws Exception {
        // just check for no throw
        final CollectedEvidence evidence = buildHook().apply(BidRequest.builder().build());
        assertThat(evidence).isNotNull();
    }

    @Test
    public void shouldAddUA() throws Exception {
        // given
        final String testUA = "MindScape Crawler";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua(testUA).build())
                .build();

        // when
        final CollectedEvidence evidence = buildHook().apply(bidRequest);

        // then
        assertThat(evidence.deviceUA()).isEqualTo(testUA);
    }

    @Test
    public void shouldAddSUA() throws Exception {
        // given
        final UserAgent testSUA = UserAgent.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().sua(testSUA).build())
                .build();

        // when
        final CollectedEvidence evidence = buildHook().apply(bidRequest);

        // then
        assertThat(evidence.secureHeaders()).isEmpty();
    }
}
