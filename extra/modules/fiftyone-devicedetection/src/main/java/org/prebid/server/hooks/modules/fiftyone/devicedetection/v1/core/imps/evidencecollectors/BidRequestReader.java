package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.evidencecollectors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.BidRequestEvidenceCollector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.MergingConfiguratorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.PropertyMergeImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence.CollectedEvidenceBuilder;

import java.util.List;

public final class BidRequestReader implements BidRequestEvidenceCollector {
    private static final MergingConfiguratorImp<CollectedEvidenceBuilder, Device> MERGER = new MergingConfiguratorImp<>(
            List.of(
                    new PropertyMergeImp<>(Device::getUa, ua -> true, CollectedEvidenceBuilder::deviceUA),
                    new PropertyMergeImp<>(Device::getSua, sua -> true, CollectedEvidenceBuilder::deviceSUA)));

    @Override
    public void accept(CollectedEvidenceBuilder evidenceBuilder, BidRequest bidRequest) {
        MERGER.applyProperties(evidenceBuilder, bidRequest.getDevice());
    }
}
