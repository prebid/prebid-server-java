package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class JacksonMapper {

    private final ObjectMapper mapper;

    public JacksonMapper(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public <T> String encode(T obj) throws EncodeException {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EncodeException("Failed to encode as JSON: " + e.getMessage());
        }
    }

    public <T> T decodeValue(String str, Class<T> clazz) throws DecodeException {
        try {
            return mapper.readValue(str, clazz);
        } catch (JsonProcessingException e) {
            throw new DecodeException("Failed to decode: " + e.getMessage());
        }
    }

    public <T> T decodeValue(String str, TypeReference<T> type) throws DecodeException {
        try {
            return mapper.readValue(str, type);
        } catch (JsonProcessingException e) {
            throw new DecodeException("Failed to decode: " + e.getMessage(), e);
        }
    }

    public <T> T decodeValue(Buffer buf, Class<T> clazz) throws DecodeException {
        try {
            return mapper.readValue((InputStream) new ByteBufInputStream(buf.getByteBuf()), clazz);
        } catch (IOException e) {
            throw new DecodeException("Failed to decode: " + e.getMessage(), e);
        }
    }
}
