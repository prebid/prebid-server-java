package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary;

import lombok.Builder;

import java.util.Collection;
import java.util.Map;

@Builder(toBuilder = true)
public record CollectedEvidence(
        Collection<Map.Entry<String, String>> rawHeaders,
        String deviceUA,
        Map<String, String> secureHeaders
) {
}
