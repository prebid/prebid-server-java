package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.ExecutionGroup;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.hooks.execution.model.HookId;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.hooks.execution.model.StageExecutionPlan;
import org.prebid.server.model.Endpoint;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.prebid.server.hooks.execution.model.HookHttpEndpoint.POST_AUCTION;

public class ExecutionPlanEndpointsConfigDeserializerTest extends VertxTest {

    @Test
    public void deserializeShouldReturnNullOnNull() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan((Map<HookHttpEndpoint, EndpointExecutionPlan>) null);

        // when
        final ExecutionPlan result = mapper.readValue(executionPlan, ExecutionPlan.class);

        // then
        assertThat(result.getEndpoints()).isNull();
    }

    @Test
    public void deserializeShouldReturnEmptyOnEmpty() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan(Collections.emptyMap());

        // when
        final ExecutionPlan result = mapper.readValue(executionPlan, ExecutionPlan.class);

        // then
        assertThat(result.getEndpoints()).isEmpty();
    }

    @Test
    public void deserializeShouldReadAllPossibleEndpoints() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan(allEndpoints());

        // when
        final ExecutionPlan result = mapper.readValue(executionPlan, ExecutionPlan.class);

        // then
        assertThat(result.getEndpoints()).containsExactlyInAnyOrderEntriesOf(allEndpoints());
    }

    @Test
    public void deserializeShouldFailOnInvalidEndpointExecutionPlan() {
        // given
        final String executionPlan = """
                {
                  "endpoints": {
                    "POST /openrtb2/auction": "invalid"
                  }
                }""";

        // when and then
        assertThatExceptionOfType(MismatchedInputException.class)
                .isThrownBy(() -> mapper.readValue(executionPlan, ExecutionPlan.class))
                .withMessageContaining("Cannot construct instance of")
                .withMessageContaining("deserialize from String value ('invalid')");
    }

    @Test
    public void deserializeShouldFailOnInvalidEndpoint() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan("invalid");

        // when and then
        assertThatExceptionOfType(InvalidFormatException.class)
                .isThrownBy(() -> mapper.readValue(executionPlan, ExecutionPlan.class))
                .withMessageContaining("Cannot deserialize Map key of type")
                .withMessageContaining("from String \"invalid\": not a valid representation");
    }

    @Test
    public void deserializeShouldFailOnInvalidHttpHookEndpoint() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan("INVALID " + Endpoint.openrtb2_auction.value());

        // when and then
        assertThatExceptionOfType(InvalidFormatException.class)
                .isThrownBy(() -> mapper.readValue(executionPlan, ExecutionPlan.class))
                .withMessageContaining("Cannot deserialize value of type")
                .withMessageContaining("""
                        from String "INVALID /openrtb2/auction": \
                        not one of the values accepted for Enum class""");
    }

    @Test
    public void deserializeShouldCorrectlyUnpackAllEndpointsWithoutMethod() throws JsonProcessingException {
        // given
        final String[] endpointsWithoutMethod = Arrays.stream(HookHttpEndpoint.values())
                .map(HookHttpEndpoint::endpoint)
                .map(Endpoint::value)
                .distinct()
                .toArray(String[]::new);
        final String executionPlan = givenExecutionPlan(endpointsWithoutMethod);

        // when
        final ExecutionPlan result = mapper.readValue(executionPlan, ExecutionPlan.class);

        // then
        assertThat(result.getEndpoints()).containsExactlyInAnyOrderEntriesOf(
                Arrays.stream(HookHttpEndpoint.values())
                        .collect(Collectors.toMap(
                                Function.identity(),
                                key -> givenEndpointExecutionPlan(key.endpoint().value()))));
    }

    @Test
    public void deserializeShouldNotOverrideInitialConfigAfterUnpacking() throws JsonProcessingException {
        // given
        final String executionPlan = givenExecutionPlan(
                POST_AUCTION.toString(),
                POST_AUCTION.endpoint().value());

        // when
        final ExecutionPlan result = mapper.readValue(executionPlan, ExecutionPlan.class);

        // when and then
        assertThat(result.getEndpoints()).containsExactlyInAnyOrderEntriesOf(
                Map.of(POST_AUCTION, givenEndpointExecutionPlan(POST_AUCTION.toString())));
    }

    private static Map<HookHttpEndpoint, EndpointExecutionPlan> allEndpoints() {
        return Arrays.stream(HookHttpEndpoint.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        key -> givenEndpointExecutionPlan(key.toString())));
    }

    private static EndpointExecutionPlan givenEndpointExecutionPlan(String id) {
        return EndpointExecutionPlan.of(Map.of(Stage.entrypoint, StageExecutionPlan.of(
                singletonList(ExecutionGroup.of(0L, singletonList(HookId.of(id, id)))))));
    }

    private static String givenExecutionPlan(Map<HookHttpEndpoint, EndpointExecutionPlan> endpoints)
            throws JsonProcessingException {

        return mapper.writeValueAsString(ExecutionPlan.of(null, endpoints));
    }

    private static String givenExecutionPlan(String... hookHttpEndpointsAsStrings) throws JsonProcessingException {
        final Map<String, EndpointExecutionPlan> endpoints = Arrays.stream(hookHttpEndpointsAsStrings)
                .collect(Collectors.toMap(
                        Function.identity(),
                        ExecutionPlanEndpointsConfigDeserializerTest::givenEndpointExecutionPlan));

        return """
                {
                  "endpoints": %s
                }""".formatted(mapper.writeValueAsString(endpoints));
    }
}
