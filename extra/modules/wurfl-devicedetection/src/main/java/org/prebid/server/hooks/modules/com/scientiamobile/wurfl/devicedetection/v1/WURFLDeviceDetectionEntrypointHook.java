package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.AuctionRequestHeadersContext;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import io.vertx.core.Future;

public class WURFLDeviceDetectionEntrypointHook implements EntrypointHook {

    private static final String CODE = "wurfl-devicedetection-entrypoint-hook";

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(EntrypointPayload entrypointPayload,
                                                            InvocationContext invocationContext) {

        final AuctionRequestHeadersContext bidRequestHeadersContext = AuctionRequestHeadersContext.from(
                entrypointPayload.headers());

        return Future.succeededFuture(
                InvocationResultImpl.<EntrypointPayload>builder()
                        .status(InvocationStatus.success)
                        .action(InvocationAction.no_action)
                        .moduleContext(bidRequestHeadersContext)
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
