package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.UserAgentEvidenceConverter;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.context.CollectedEvidence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class PriorityEvidenceSelectorImp implements PriorityEvidenceSelector {
    private final UserAgentEvidenceConverter userAgentEvidenceConverter;

    public PriorityEvidenceSelectorImp(UserAgentEvidenceConverter userAgentEvidenceConverter) {
        this.userAgentEvidenceConverter = userAgentEvidenceConverter;
    }

    @Override
    public Map<String, String> apply(CollectedEvidence collectedEvidence) {
        final Map<String, String> evidence = new HashMap<>();

        final String ua = collectedEvidence.deviceUA();
        if (ua != null && !ua.isEmpty()) {
            evidence.put("header.user-agent", ua);
        }
        userAgentEvidenceConverter.unpack(collectedEvidence.deviceSUA(), evidence);
        if (!evidence.isEmpty()) {
            return evidence;
        }

        final Collection<Map.Entry<String, String>> headers = collectedEvidence.rawHeaders();
        if (headers != null && !headers.isEmpty()) {
            for(Map.Entry<String, String> nextRawHeader : headers) {
                evidence.put("header." + nextRawHeader.getKey(), nextRawHeader.getValue());
            }
        }

        return evidence;
    }
}
