package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.UserAgent;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import static org.assertj.core.api.Assertions.assertThat;

public class BidRequestReaderTest {
    @Test
    public void shouldNotFailOnNoDevice() {
        // just check for no throw
        new BidRequestReader().evidenceFrom(BidRequest.builder().build()).injectInto(null);
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
        new BidRequestReader().evidenceFrom(bidRequest).injectInto(evidenceBuilder);
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
        new BidRequestReader().evidenceFrom(bidRequest).injectInto(evidenceBuilder);
        final CollectedEvidence evidence = evidenceBuilder.build();

        // then
        assertThat(evidence.deviceSUA()).isEqualTo(testSUA);
    }
}
