package org.prebid.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;

import java.io.IOException;
import java.util.Objects;

// TODO: refactor to be instance instead of util
public class JsonMergeUtil {

    private final JacksonMapper mapper;

    public JsonMergeUtil(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Merges passed object with json retrieved from stored data map by id
     * and cast it to appropriate class. In case of any exception during merging, throws {@link InvalidRequestException}
     * with reason message.
     */
    public <T> T merge(T originalObject, String storedData, String id, Class<T> classToCast) {
        final JsonNode originJsonNode = mapper.mapper().valueToTree(originalObject);
        final JsonNode storedRequestJsonNode;
        try {
            storedRequestJsonNode = mapper.mapper().readTree(storedData);
        } catch (IOException e) {
            throw new InvalidRequestException(
                    String.format("Can't parse Json for stored request with id %s", id));
        }
        try {
            // Http request fields have higher priority and will override fields from stored requests
            // in case they have different values
            return mapper.mapper().treeToValue(JsonMergePatch.fromJson(originJsonNode).apply(storedRequestJsonNode),
                    classToCast);
        } catch (JsonPatchException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch from origin object node for id %s: %s", id, e.getMessage()));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(
                    String.format("Can't convert merging result for id %s: %s", id, e.getMessage()));
        }
    }

    public <T> T merge(T originalObject, T mergingObject, Class<T> classToCast) {
        final JsonNode originJsonNode = mapper.mapper().valueToTree(originalObject);
        final JsonNode mergingObjectJsonNode = mapper.mapper().valueToTree(mergingObject);
        try {
            final JsonNode mergedNode = JsonMergePatch.fromJson(originJsonNode).apply(mergingObjectJsonNode);
            return mapper.mapper().treeToValue(mergedNode, classToCast);
        } catch (JsonPatchException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch for objects with class %s", classToCast.getName()));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(
                    String.format("Can't convert merging result class %s", classToCast.getName()));
        }
    }
}
