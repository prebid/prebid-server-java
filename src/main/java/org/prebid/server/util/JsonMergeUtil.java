package org.prebid.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;

import java.io.IOException;
import java.util.Objects;

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
        final JsonNode mergingObject;
        try {
            mergingObject = mapper.mapper().readTree(storedData);
        } catch (IOException e) {
            throw new InvalidRequestException(
                    String.format("Can't parse Json for stored request with id %s", id));
        }

        try {
            return mergeJsons(originJsonNode, mergingObject, classToCast);
        } catch (InvalidRequestException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch from origin object node for id %s: %s", id, e.getMessage()));
        }
    }

    public <T> T merge(T originalObject, T mergingObject, Class<T> classToCast) {
        if (!ObjectUtils.allNotNull(originalObject, mergingObject)) {
            return ObjectUtils.firstNonNull(originalObject, mergingObject);
        }
        final JsonNode originJsonNode = mapper.mapper().valueToTree(originalObject);
        final JsonNode mergingObjectJsonNode = mapper.mapper().valueToTree(mergingObject);
        return mergeJsons(originJsonNode, mergingObjectJsonNode, classToCast);
    }

    public <T, V> T mergeFamiliar(V originalObject, T mergingObject, Class<T> classToCast) {
        if (originalObject == null) {
            return mergingObject;
        }
        final JsonNode originJsonNode = mapper.mapper().valueToTree(originalObject);
        final JsonNode mergingObjectJsonNode = mapper.mapper().valueToTree(mergingObject);
        return mergeJsons(originJsonNode, mergingObjectJsonNode, classToCast);
    }

    private <T> T mergeJsons(JsonNode originJsonNode, JsonNode mergingObjectJsonNode, Class<T> classToCast) {
        try {
            final JsonNode mergedNode = mergeJsons(originJsonNode, mergingObjectJsonNode);
            return mapper.mapper().treeToValue(mergedNode, classToCast);
        } catch (InvalidRequestException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch for objects with class %s", classToCast.getName()));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format(
                    "Can't convert merging result class %s", classToCast.getName()));
        }
    }

    public JsonNode mergeJsons(JsonNode originJsonNode, JsonNode mergingObjectJsonNode) {
        if (!ObjectUtils.allNotNull(originJsonNode, mergingObjectJsonNode)) {
            return ObjectUtils.firstNonNull(originJsonNode, mergingObjectJsonNode);
        }
        try {
            // Http request fields have higher priority and will override fields from stored requests
            // in case they have different values
            return JsonMergePatch.fromJson(originJsonNode).apply(mergingObjectJsonNode);
        } catch (JsonPatchException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch for objects %s", originJsonNode));
        }
    }
}
