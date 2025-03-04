package org.prebid.server.hooks.execution.provider.abtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import org.prebid.server.auction.model.Rejected;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.analytics.Activity;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ABTestHook<PAYLOAD, CONTEXT extends InvocationContext> implements Hook<PAYLOAD, CONTEXT> {

    private static final String ANALYTICS_ACTIVITY_NAME = "core-module-abtests";

    private final String moduleName;
    private final Hook<PAYLOAD, CONTEXT> hook;
    private final boolean shouldInvokeHook;
    private final boolean logABTestAnalyticsTag;
    private final ObjectMapper mapper;

    public ABTestHook(String moduleName,
                      Hook<PAYLOAD, CONTEXT> hook,
                      boolean shouldInvokeHook,
                      boolean logABTestAnalyticsTag,
                      ObjectMapper mapper) {

        this.moduleName = Objects.requireNonNull(moduleName);
        this.hook = Objects.requireNonNull(hook);
        this.shouldInvokeHook = shouldInvokeHook;
        this.logABTestAnalyticsTag = logABTestAnalyticsTag;
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public String code() {
        return hook.code();
    }

    @Override
    public Future<InvocationResult<PAYLOAD>> call(PAYLOAD payload, CONTEXT invocationContext) {
        if (!shouldInvokeHook) {
            return skippedResult();
        }

        final Future<InvocationResult<PAYLOAD>> invocationResultFuture = hook.call(payload, invocationContext);
        return logABTestAnalyticsTag
                ? invocationResultFuture.map(this::enrichWithABTestAnalyticsTag)
                : invocationResultFuture;
    }

    private Future<InvocationResult<PAYLOAD>> skippedResult() {
        return Future.succeededFuture(InvocationResultImpl.<PAYLOAD>builder()
                .status(InvocationStatus.success)
                .action(InvocationAction.no_invocation)
                .analyticsTags(logABTestAnalyticsTag ? tags("skipped") : null)
                .build());
    }

    private Tags tags(String status) {
        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                ANALYTICS_ACTIVITY_NAME,
                "success",
                Collections.singletonList(ResultImpl.of(status, analyticsValues(), null)))));
    }

    private ObjectNode analyticsValues() {
        final ObjectNode values = mapper.createObjectNode();
        values.put("module", moduleName);
        return values;
    }

    private InvocationResult<PAYLOAD> enrichWithABTestAnalyticsTag(InvocationResult<PAYLOAD> invocationResult) {
        return new InvocationResultWithAdditionalTags<>(invocationResult, tags("run"));
    }

    private record InvocationResultWithAdditionalTags<PAYLOAD>(InvocationResult<PAYLOAD> invocationResult,
                                                               Tags additionalTags)
            implements InvocationResult<PAYLOAD> {

        @Override
        public InvocationStatus status() {
            return invocationResult.status();
        }

        @Override
        public String message() {
            return invocationResult.message();
        }

        @Override
        public InvocationAction action() {
            return invocationResult.action();
        }

        @Override
        public PayloadUpdate<PAYLOAD> payloadUpdate() {
            return invocationResult.payloadUpdate();
        }

        @Override
        public List<String> errors() {
            return invocationResult.errors();
        }

        @Override
        public List<String> warnings() {
            return invocationResult.warnings();
        }

        @Override
        public Map<String, List<Rejected>> rejections() {
            return invocationResult.rejections();
        }

        @Override
        public List<String> debugMessages() {
            return invocationResult.debugMessages();
        }

        @Override
        public Object moduleContext() {
            return invocationResult.moduleContext();
        }

        @Override
        public Tags analyticsTags() {
            return new TagsUnion(invocationResult.analyticsTags(), additionalTags);
        }
    }

    private record TagsUnion(Tags left, Tags right) implements Tags {

        @Override
        public List<Activity> activities() {
            return left != null
                    ? ListUtil.union(left.activities(), right.activities())
                    : right.activities();
        }
    }
}
