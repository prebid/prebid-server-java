package org.prebid.server.hooks.execution;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.settings.model.Account;

public class HookStageExecutor {

    public Future<HookStageExecutionResult<EntrypointPayload>> executeEntrypointStage(
            MultiMap queryParams,
            MultiMap headers,
            String body,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, new EntrypointPayload() {
            @Override
            public MultiMap queryParams() {
                return queryParams;
            }

            @Override
            public MultiMap headers() {
                return headers;
            }

            @Override
            public String body() {
                return body;
            }
        }));
    }

    public Future<HookStageExecutionResult<AuctionRequestPayload>> executeRawAuctionRequestStage(
            BidRequest bidRequest,
            Account account,
            HookExecutionContext context) {

        return Future.succeededFuture(HookStageExecutionResult.of(false, () -> bidRequest));
    }
}
