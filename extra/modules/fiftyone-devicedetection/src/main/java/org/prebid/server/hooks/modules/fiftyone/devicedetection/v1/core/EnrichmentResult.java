package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.Device;
import lombok.Builder;

import java.util.Collection;

@Builder
public record EnrichmentResult(
        Device enrichedDevice,
        Collection<String> enrichedFields) {
}
