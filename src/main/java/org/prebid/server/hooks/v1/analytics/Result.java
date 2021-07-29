package org.prebid.server.hooks.v1.analytics;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface Result {

    String status();

    ObjectNode values();

    AppliedTo appliedTo();
}
