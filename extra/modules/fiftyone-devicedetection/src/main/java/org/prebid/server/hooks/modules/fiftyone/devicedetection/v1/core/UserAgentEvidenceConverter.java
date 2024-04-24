package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import com.iab.openrtb.request.UserAgent;

import java.util.Map;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface UserAgentEvidenceConverter extends BiConsumer<UserAgent, Map<String, String>> {
    default void unpack(UserAgent userAgent, Map<String, String> outMap) {
        accept(userAgent, outMap);
    }
}
