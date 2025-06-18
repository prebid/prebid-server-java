package org.prebid.server.hooks.modules.rule.engine.core.request.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class AdUnitCodeUtils {

    private AdUnitCodeUtils() {
    }

    public static Optional<String> extractAdUnitCode(Imp imp) {
        return extractGpid(imp)
                .or(() -> extractTagId(imp))
                .or(() -> extractPbAdSlot(imp))
                .or(() -> extractStoredRequestId(imp));
    }

    public static Optional<String> extractGpid(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("gpid"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    public static Optional<String> extractTagId(Imp imp) {
        return Optional.ofNullable(imp.getTagid())
                .filter(StringUtils::isNotBlank);
    }

    public static Optional<String> extractPbAdSlot(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("data"))
                .map(data -> data.get("pbadslot"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }

    public static Optional<String> extractStoredRequestId(Imp imp) {
        return Optional.ofNullable(imp.getExt())
                .map(ext -> ext.get("prebid"))
                .map(prebid -> prebid.get("storedrequest"))
                .map(storedRequest -> storedRequest.get("id"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText);
    }
}
