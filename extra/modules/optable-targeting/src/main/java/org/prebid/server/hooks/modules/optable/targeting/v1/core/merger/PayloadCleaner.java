package org.prebid.server.hooks.modules.optable.targeting.v1.core.merger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class PayloadCleaner {

    private static final List<String> FIELDS_FILTER = List.of("email", "phone", "zip", "vid");

    public ObjectNode cleanUserExtOptable(ObjectNode optable) {
        return optable.deepCopy().remove(FIELDS_FILTER);
    }
}
