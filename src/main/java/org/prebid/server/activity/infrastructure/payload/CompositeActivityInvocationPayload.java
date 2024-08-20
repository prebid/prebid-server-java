package org.prebid.server.activity.infrastructure.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.activity.infrastructure.debug.Loggable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CompositeActivityInvocationPayload implements ActivityInvocationPayload, Loggable {

    private final Map<Class<? extends ActivityInvocationPayload>, ActivityInvocationPayload> payloads;

    public CompositeActivityInvocationPayload(Collection<ActivityInvocationPayload> payloads) {
        this.payloads = Objects.requireNonNull(payloads).stream()
                .collect(Collectors.toMap(
                        CompositeActivityInvocationPayload::resolveType,
                        Function.identity()));

        validate(this.payloads);
    }

    private static Class<? extends ActivityInvocationPayload> resolveType(ActivityInvocationPayload payload) {
        return switch (payload) {
            case ComponentActivityInvocationPayload ignored -> ComponentActivityInvocationPayload.class;
            case GeoActivityInvocationPayload ignored -> ComponentActivityInvocationPayload.class;
            case GpcActivityInvocationPayload ignored -> ComponentActivityInvocationPayload.class;
            case null, default -> throw new IllegalArgumentException("Illegal payload.");
        };
    }

    private static void validate(Map<Class<? extends ActivityInvocationPayload>, ActivityInvocationPayload> payloads) {
        if (!payloads.containsKey(ComponentActivityInvocationPayload.class)) {
            throw new IllegalArgumentException("\"Component\" payload must be provided.");
        }
    }

    public <T extends ActivityInvocationPayload> boolean hasPayload(Class<T> type) {
        return payloads.containsKey(type);
    }

    public <T extends ActivityInvocationPayload> T get(Class<T> type) {
        //noinspection unchecked
        return (T) payloads.get(type);
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        final ObjectNode logEntry = mapper.createObjectNode();
        for (ActivityInvocationPayload payload : payloads.values()) {
            logEntry.setAll((ObjectNode) mapper.valueToTree(payload));
        }
        return logEntry;
    }
}
