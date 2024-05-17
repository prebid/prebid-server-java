package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;

import java.util.Map;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestReaderTest {
    private static BiConsumer<CollectedEvidence.CollectedEvidenceBuilder, BidRequest> buildHook(
            BiConsumer<UserAgent, Map<String, String>> userAgentEvidenceConverter)
    {
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(
                null,
                null,
                null,
                null
        ) {
            @Override
            public void collectEvidence(CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
                super.collectEvidence(evidenceBuilder, bidRequest);
            }

            @Override
            protected void appendSecureHeaders(UserAgent userAgent, Map<String, String> evidence) {
                userAgentEvidenceConverter.accept(userAgent, evidence);
            }
        }::collectEvidence;
    }

    @Test
    public void shouldNotFailOnNoDevice() {
        // just check for no throw
        buildHook(null).accept(null, BidRequest.builder().build());
    }

    @Test
    public void shouldAddUA() {
        // given
        final String testUA = "MindScape Crawler";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua(testUA).build())
                .build();

        // when
        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = CollectedEvidence.builder();
        buildHook(null).accept(evidenceBuilder, bidRequest);
        final CollectedEvidence evidence = evidenceBuilder.build();

        // then
        assertThat(evidence.deviceUA()).isEqualTo(testUA);
    }

    @Test
    public void shouldAddSUA() {
        // given
        final UserAgent testSUA = UserAgent.builder().build();
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().sua(testSUA).build())
                .build();

        // when
        final CollectedEvidence.CollectedEvidenceBuilder evidenceBuilder = CollectedEvidence.builder();
        buildHook((sua, headers) -> {}).accept(evidenceBuilder, bidRequest);
        final CollectedEvidence evidence = evidenceBuilder.build();

        // then
        assertThat(evidence.secureHeaders()).isEmpty();
    }
}
