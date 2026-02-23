package org.prebid.server.json.merge;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;

/**
 * Replicates functionality from {@link com.github.fge.jsonpatch.mergepatch.NonObjectMergePatch}
 */
final class NonObjectMergePatch extends JsonMergePatch {

    private final JsonNode node;

    NonObjectMergePatch(final JsonNode node) {
        if (node == null) {
            throw new NullPointerException();
        }
        this.node = node;
    }

    @Override
    public JsonNode apply(final JsonNode input) {
        BUNDLE.checkNotNull(input, "jsonPatch.nullValue");
        return node;
    }

    @Override
    public void serialize(final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
        jgen.writeTree(node);
    }

    @Override
    public void serializeWithType(final JsonGenerator jgen,
                                  final SerializerProvider provider,
                                  final TypeSerializer typeSer) throws IOException {

        serialize(jgen, provider);
    }
}
