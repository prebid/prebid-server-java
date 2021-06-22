package org.prebid.server.it.hooks;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationResultImpl;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.CaseInsensitiveMultiMap;

public class SampleItEntrypointHook implements EntrypointHook {

    @Override
    public Future<InvocationResult<EntrypointPayload>> call(
            EntrypointPayload entrypointPayload, InvocationContext invocationContext) {

        final boolean rejectFlag = Boolean.parseBoolean(entrypointPayload.queryParams().get("sample-it-module-reject"));
        if (rejectFlag) {
            return Future.succeededFuture(InvocationResultImpl.rejected("Rejected by sample entrypoint hook"));
        }

        return maybeUpdate(entrypointPayload);
    }

    private Future<InvocationResult<EntrypointPayload>> maybeUpdate(EntrypointPayload entrypointPayload) {
        final String updateSelector = entrypointPayload.queryParams().get("sample-it-module-update");

        final CaseInsensitiveMultiMap updatedHeaders = StringUtils.contains(updateSelector, "headers")
                ? updateHeaders(entrypointPayload.headers())
                : entrypointPayload.headers();

        final String updatedBody = StringUtils.contains(updateSelector, "body")
                ? updateBody(entrypointPayload.body())
                : entrypointPayload.body();

        return Future.succeededFuture(InvocationResultImpl.succeeded(payload -> EntrypointPayloadImpl.of(
                payload.queryParams(),
                updatedHeaders,
                updatedBody)));
    }

    private static CaseInsensitiveMultiMap updateHeaders(CaseInsensitiveMultiMap headers) {
        return CaseInsensitiveMultiMap.builder()
                .addAll(headers)
                .add("X-Forwarded-For", "222.111.222.111")
                .build();
    }

    private static String updateBody(String body) {
        return body.replaceAll("\"language\":\"en\"", "\"language\":\"fr\"");
    }

    @Override
    public String code() {
        return "entrypoint";
    }
}
