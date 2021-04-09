package org.prebid.server.hooks.execution;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.v1.HookCatalog;
import org.prebid.server.hooks.v1.entrypoint.EntrypointPayload;
import org.prebid.server.model.Endpoint;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class HookStageExecutorTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HookCatalog hookCatalog;
    @Mock
    private TimeoutFactory timeoutFactory;
    @Mock
    private Vertx vertx;
    @Mock
    private Clock clock;

    @Test
    public void creationShouldFailWhenExecutionPlanIsInvalid() {
        Assertions.assertThatThrownBy(() -> HookStageExecutor.create(
                "{endpoints: {abc: {}}}",
                hookCatalog,
                timeoutFactory,
                vertx,
                clock,
                jacksonMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hooks execution plan could not be parsed");
    }

    @Test
    public void shouldTolerateMissingExecutionPlan() {
        // given
        final HookStageExecutor executor = HookStageExecutor.create(
                null,
                hookCatalog,
                timeoutFactory,
                vertx,
                clock,
                jacksonMapper);

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

        HookStageExecutor.create(plan, hookCatalog, timeoutFactory, vertx, clock, jacksonMapper);
    }
}
