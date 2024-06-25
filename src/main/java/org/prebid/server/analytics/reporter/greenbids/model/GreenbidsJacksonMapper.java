package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.prebid.server.json.EncodeException;

import java.util.Objects;

public class GreenbidsJacksonMapper {

    private final ObjectMapper mapper;

    public GreenbidsJacksonMapper(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper)
                .setPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public <T> String encodeToString(T obj) throws EncodeException {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
        }
    }
}
