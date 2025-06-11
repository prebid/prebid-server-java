package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;

public class ExtMerger {

    private ExtMerger() {
    }

    public static ObjectNode mergeExt(ObjectNode origin, ObjectNode newExt) {
        if (newExt == null) {
            return origin;
        }

        return Optional.ofNullable(origin)
                .map(it -> {
                    newExt.fieldNames().forEachRemaining(field -> it.set(field, newExt.get(field)));
                    return it;
                }).orElse(newExt);
    }
}
