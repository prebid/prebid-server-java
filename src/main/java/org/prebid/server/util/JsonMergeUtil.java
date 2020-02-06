package org.prebid.server.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import io.vertx.core.json.Json;
import org.prebid.server.exception.InvalidRequestException;

import java.io.IOException;

public class JsonMergeUtil {

    private JsonMergeUtil() {
    }

    /**
     * Merges passed object with json retrieved from stored data map by id
     * and cast it to appropriate class. In case of any exception during merging, throws {@link InvalidRequestException}
     * with reason message.
     */
    public static <T> T merge(T originalObject, String storedData, String id, Class<T> classToCast) {
        final JsonNode originJsonNode = Json.mapper.valueToTree(originalObject);
        final JsonNode storedRequestJsonNode;
        try {
            storedRequestJsonNode = Json.mapper.readTree(storedData);
        } catch (IOException e) {
            throw new InvalidRequestException(
                    String.format("Can't parse Json for stored request with id %s", id));
        }
        try {
            // Http request fields have higher priority and will override fields from stored requests
            // in case they have different values
            return Json.mapper.treeToValue(JsonMergePatch.fromJson(originJsonNode).apply(storedRequestJsonNode),
                    classToCast);
        } catch (JsonPatchException e) {
            throw new InvalidRequestException(String.format(
                    "Couldn't create merge patch from origin object node for id %s: %s", id, e.getMessage()));
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(
                    String.format("Can't convert merging result for id %s: %s", id, e.getMessage()));
        }
    }

}
