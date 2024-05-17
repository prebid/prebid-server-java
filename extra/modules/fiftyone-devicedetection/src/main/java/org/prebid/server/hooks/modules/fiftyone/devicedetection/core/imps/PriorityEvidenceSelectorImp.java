package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.PriorityEvidenceSelector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class PriorityEvidenceSelectorImp implements PriorityEvidenceSelector {

    @Override
    public Map<String, String> apply(CollectedEvidence collectedEvidence) {
        final Map<String, String> evidence = new HashMap<>();

        final String ua = collectedEvidence.deviceUA();
        if (ua != null && !ua.isEmpty()) {
            evidence.put("header.user-agent", ua);
        }
        final Map<String, String> secureHeaders = collectedEvidence.secureHeaders();
        if (secureHeaders != null && !secureHeaders.isEmpty()) {
            evidence.putAll(secureHeaders);
        }
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
