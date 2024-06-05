package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.ModuleContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;

import java.util.HashMap;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModuleContextPatcherImpTest {
    private static BiFunction<
            ModuleContext,
            Device,
            ModuleContext> buildPatcher() throws Exception {
        final RawAuctionRequestHook hook = new FiftyOneDeviceDetectionRawAuctionRequestHook(
                mock(ModuleConfig.class),
                mock(DeviceEnricher.class)
        );
        return (moduleContext, device) -> {
            final BidRequest bidRequest = BidRequest.builder()
                    .device(device)
                    .build();
            final AuctionRequestPayload auctionRequestPayload = mock(AuctionRequestPayload.class);
            when(auctionRequestPayload.bidRequest()).thenReturn(bidRequest);
            final AuctionInvocationContext invocationContext = mock(AuctionInvocationContext.class);
            when(invocationContext.moduleContext()).thenReturn(moduleContext);
            return (ModuleContext) hook.call(auctionRequestPayload, invocationContext)
                    .result()
                    .moduleContext();
        };
    }

    @Test
    public void shouldMakeNewContextIfNullIsPassedIn() throws Exception {
        // given and when
        final ModuleContext newContext = buildPatcher().apply(null, null);

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void shouldMakeNewEvidenceIfNoneWasPresent() throws Exception {
        // given and when
        final ModuleContext newContext = buildPatcher().apply(
                ModuleContext.builder().build(),
                null);

        // then
        assertThat(newContext).isNotNull();
        assertThat(newContext.collectedEvidence()).isNotNull();
    }

    @Test
    public void shouldMergeEvidences() throws Exception {
        // given and when
        final String ua = "mad-hatter";
        final HashMap<String, String> sua = new HashMap<>();
        final ModuleContext existingContext = ModuleContext.builder()
                .collectedEvidence(CollectedEvidence.builder()
                        .secureHeaders(sua)
                        .build())
                .build();

        // when
        final ModuleContext newContext = buildPatcher().apply(
                existingContext,
                Device.builder().ua(ua).build());

        // then
        assertThat(newContext).isNotNull();
        final CollectedEvidence newEvidence = newContext.collectedEvidence();
        assertThat(newEvidence).isNotNull();
        assertThat(newEvidence.deviceUA()).isEqualTo(ua);
        assertThat(newEvidence.secureHeaders()).isEqualTo(sua);
    }
}
