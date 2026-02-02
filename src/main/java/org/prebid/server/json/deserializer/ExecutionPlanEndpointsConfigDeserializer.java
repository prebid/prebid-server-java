package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.hooks.execution.model.EndpointExecutionPlan;
import org.prebid.server.hooks.execution.model.HookHttpEndpoint;
import org.prebid.server.model.Endpoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class ExecutionPlanEndpointsConfigDeserializer
        extends StdDeserializer<Map<HookHttpEndpoint, EndpointExecutionPlan>> {

    private static final TypeReference<Map<ConfigKey, EndpointExecutionPlan>> TEMP_MAP_REFERENCE =
            new TypeReference<>() {
            };

    protected ExecutionPlanEndpointsConfigDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<HookHttpEndpoint, EndpointExecutionPlan> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        final Map<ConfigKey, EndpointExecutionPlan> tempMap = parser.readValueAs(TEMP_MAP_REFERENCE);
        if (tempMap == null) {
            return null;
        }
        if (tempMap.isEmpty()) {
            return Collections.emptyMap();
        }

        return Collections.unmodifiableMap(convertKeys(unpack(tempMap), context));
    }

    private static Map<ConfigKey, EndpointExecutionPlan> unpack(Map<ConfigKey, EndpointExecutionPlan> map) {
        final Map<ConfigKey, EndpointExecutionPlan> unpacked = new HashMap<>(map);
        for (ConfigKey key : map.keySet()) {
            final HttpMethod httpMethod = key.httpMethod();
            if (httpMethod != null) {
                continue;
            }

            final Endpoint endpoint = key.endpoint();
            final EndpointExecutionPlan value = unpacked.remove(key);

            Arrays.stream(HookHttpEndpoint.values())
                    .filter(httpEndpoint -> httpEndpoint.endpoint() == endpoint)
                    .map(HookHttpEndpoint::httpMethod)
                    .forEach(newHttpMethod -> unpacked.putIfAbsent(new ConfigKey(newHttpMethod, endpoint), value));
        }

        return unpacked;
    }

    private static Map<HookHttpEndpoint, EndpointExecutionPlan> convertKeys(Map<ConfigKey, EndpointExecutionPlan> map,
                                                                            DeserializationContext context)
            throws JsonMappingException {

        final Map<HookHttpEndpoint, EndpointExecutionPlan> result = new EnumMap<>(HookHttpEndpoint.class);
        for (Map.Entry<ConfigKey, EndpointExecutionPlan> entry : map.entrySet()) {
            result.put(convertKey(entry.getKey(), context), entry.getValue());
        }

        return result;
    }

    private static HookHttpEndpoint convertKey(ConfigKey configKey, DeserializationContext context)
            throws JsonMappingException {

        return Arrays.stream(HookHttpEndpoint.values())
                .filter(endpoint -> endpoint.endpoint() == configKey.endpoint()
                        && endpoint.httpMethod().equals(configKey.httpMethod()))
                .findFirst()
                .orElseThrow(() -> context.weirdStringException(
                        configKey.toString(),
                        HookHttpEndpoint.class,
                        "not one of the values accepted for Enum class: %s"
                                .formatted(Arrays.toString(HookHttpEndpoint.values()))));
    }

    private record ConfigKey(HttpMethod httpMethod, Endpoint endpoint) {

        @SuppressWarnings("unused")
        @JsonCreator
        public static ConfigKey fromString(String value) {
            if (value == null) {
                return null;
            }

            final int delimiterIndex = value.indexOf(' ');
            final HttpMethod httpMethod = delimiterIndex != -1
                    ? HttpMethod.valueOf(value.substring(0, delimiterIndex))
                    : null;

            return new ConfigKey(httpMethod, Endpoint.fromString(value));
        }

        @Override
        public String toString() {
            return httpMethod.name() + " " + endpoint.value();
        }
    }
}
