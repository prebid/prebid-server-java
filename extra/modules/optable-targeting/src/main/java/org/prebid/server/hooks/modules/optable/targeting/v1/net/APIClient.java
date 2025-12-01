package org.prebid.server.hooks.modules.optable.targeting.v1.net;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;

import java.util.List;

public interface APIClient {

    Future<TargetingResult> getTargeting(OptableTargetingProperties properties,
                                         Query query,
                                         List<String> ips,
                                         String userAgent,
                                         Timeout timeout);
}
