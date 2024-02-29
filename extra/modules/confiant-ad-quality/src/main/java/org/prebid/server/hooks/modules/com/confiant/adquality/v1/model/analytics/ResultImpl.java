package org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class ResultImpl implements Result {

    String status;

    ObjectNode values;

    AppliedTo appliedTo;
}
