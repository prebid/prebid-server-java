package org.prebid.server.hooks.modules.optable.targeting.v1;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AnalyticTagsResolver;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;

public class OptableHook {

    private OptableHook() {
    }

    public static boolean isTargetingPropertiesValid(OptableTargetingProperties properties) {
        return !StringUtils.isEmpty(properties.getOrigin()) && !StringUtils.isEmpty(properties.getTenant());
    }

    public static <T> Future<InvocationResult<T>> update(
            PayloadUpdate<T> payloadUpdate,
            ModuleContext moduleContext) {

        return Future.succeededFuture(
                InvocationResultImpl.<T>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.update)
                        .analyticsTags(AnalyticTagsResolver.toEnrichRequestAnalyticTags(moduleContext))
                        .payloadUpdate(payloadUpdate)
                        .moduleContext(moduleContext)
                        .build());
    }
}
