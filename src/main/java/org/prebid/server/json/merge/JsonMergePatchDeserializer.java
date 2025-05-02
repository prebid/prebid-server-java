package org.prebid.server.json.merge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.prebid.server.json.ObjectMapperProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Replicates functionality from {@link com.github.fge.jsonpatch.mergepatch.JsonMergePatchDeserializer}
 */
final class JsonMergePatchDeserializer extends JsonDeserializer<JsonMergePatch> {

    /*
     * FIXME! UGLY! HACK!
     *
     * We MUST have an ObjectCodec ready so that the parser in .deserialize()
     * can actually do something useful -- for instance, deserializing even a
     * JsonNode.
     *
     * Jackson does not do this automatically; I don't know why...
     */
    private static final ObjectCodec CODEC = ObjectMapperProvider.mapper();

    @Override
    public JsonMergePatch deserialize(final JsonParser jp, final DeserializationContext ctxt) throws IOException {
        // FIXME: see comment above
        jp.setCodec(CODEC);
        final JsonNode node = jp.readValueAsTree();

        /*
         * Not an object: the simple case
         */
        if (!node.isObject()) {
            return new NonObjectMergePatch(node);
        }

        /*
         * The complicated case...
         *
         * We have to build a set of removed members, plus a map of modified
         * members.
         */

        final Set<String> removedMembers = new HashSet<>();
        final Map<String, JsonMergePatch> modifiedMembers = new HashMap<>();
        final Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();

        Map.Entry<String, JsonNode> entry;

        while (iterator.hasNext()) {
            entry = iterator.next();
            if (entry.getValue().isNull()) {
                removedMembers.add(entry.getKey());
            } else {
                final JsonMergePatch value = deserialize(entry.getValue().traverse(), ctxt);
                modifiedMembers.put(entry.getKey(), value);
            }
        }

        return new ObjectMergePatch(removedMembers, modifiedMembers);
    }

    /*
     * This method MUST be overriden... The default is to return null, which is
     * not what we want.
     */
    @Override
    @SuppressWarnings("deprecation")
    public JsonMergePatch getNullValue() {
        return new NonObjectMergePatch(NullNode.getInstance());
    }
}
