package org.prebid.server.json.merge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.JsonPatchMessages;
import com.github.fge.jsonpatch.Patch;
import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.load.MessageBundles;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;

/**
 * Json merge patch implementation that uses the application-wide object mapper.
 * Replicates functionality from {@link com.github.fge.jsonpatch.mergepatch.JsonMergePatch}.
 */
@JsonDeserialize(using = JsonMergePatchDeserializer.class)
public abstract class JsonMergePatch implements JsonSerializable, Patch {

    private static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();
    public static final MessageBundle BUNDLE = MessageBundles.getBundle(JsonPatchMessages.class);

    public static JsonMergePatch fromJson(JsonNode node) throws JsonPatchException {
        BUNDLE.checkNotNull(node, "jsonPatch.nullInput");
        try {
            return MAPPER.readValue(node.traverse(), JsonMergePatch.class);
        } catch (IOException e) {
            throw new JsonPatchException(BUNDLE.getMessage("jsonPatch.deserFailed"), e);
        }
    }

    @Override
    public abstract JsonNode apply(JsonNode input) throws JsonPatchException;
}
