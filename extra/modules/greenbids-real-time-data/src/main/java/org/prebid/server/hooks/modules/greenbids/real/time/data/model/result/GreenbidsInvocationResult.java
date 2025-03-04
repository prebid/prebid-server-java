package org.prebid.server.hooks.modules.greenbids.real.time.data.model.result;

import lombok.Value;
import org.prebid.server.hooks.v1.InvocationAction;

@Value(staticConstructor = "of")
public class GreenbidsInvocationResult {

    InvocationAction invocationAction;

    AnalyticsResult analyticsResult;
}
