package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class HookStageExecutorTest extends VertxTest {

    @Test
    public void creationShouldFailWhenExecutionPlanIsInvalid() {
        Assertions.assertThatThrownBy(() -> HookStageExecutor.create("{endpoints: {abc: {}}}", jacksonMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hooks execution plan could not be parsed");
    }

    @Test
    public void shouldTolerateMissingExecutionPlan() {
        // given
        final HookStageExecutor executor = HookStageExecutor.create(null, jacksonMapper);

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        final String body = "body";

        // when
        final Future<HookStageExecutionResult<EntrypointPayload>> future = executor.executeEntrypointStage(
                queryParams, headers, body, HookExecutionContext.of(Endpoint.openrtb2_auction));

        // then
        assertThat(future).isSucceeded();

        final EntrypointPayload payload = future.result().getPayload();
        assertThat(payload.queryParams()).isSameAs(queryParams);
        assertThat(payload.headers()).isSameAs(headers);
        assertThat(payload.body()).isSameAs(body);
    }

    @Test
    public void shouldParsePlan() {
        final String plan = ""
                + "{"
                + "\"endpoints\": {"
                + "  \"/openrtb2/auction\": {"
                + "    \"stages\": {"
                + "      \"entrypoint\": {"
                + "        \"groups\": [{"
                + "          \"synchronous\": true,"
                + "          \"timeout\": 1000,"
                + "          \"hook-sequence\": [{"
                + "            \"module-code\": \"sample-module\","
                + "            \"hook-impl-code\": \"sample-entrypoint-hook\""
                + "}]}]}}}}}";

        HookStageExecutor.create(plan, jacksonMapper);
    }
}
