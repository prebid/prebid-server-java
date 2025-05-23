package org.prebid.server.json.merge;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jsonpatch.JsonPatchException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Replicates functionality from {@link com.github.fge.jsonpatch.mergepatch.ObjectMergePatch}
 */
final class ObjectMergePatch extends JsonMergePatch {

    private final Set<String> removedMembers;
    private final Map<String, JsonMergePatch> modifiedMembers;

    ObjectMergePatch(final Set<String> removedMembers, final Map<String, JsonMergePatch> modifiedMembers) {

        this.removedMembers = Set.copyOf(removedMembers);
        this.modifiedMembers = Map.copyOf(modifiedMembers);
    }

    @Override
    public JsonNode apply(final JsonNode input)
            throws JsonPatchException {
        BUNDLE.checkNotNull(input, "jsonPatch.nullValue");
        /*
         * If the input is an object, we make a deep copy of it
         */
        final ObjectNode ret = input.isObject() ? (ObjectNode) input.deepCopy()
                : JacksonUtils.nodeFactory().objectNode();

        /*
         * Our result is now a JSON Object; first, add (or modify) existing
         * members in the result
         */
        String key;
        JsonNode value;
        for (final Map.Entry<String, JsonMergePatch> entry : modifiedMembers.entrySet()) {

            key = entry.getKey();
            /*
             * FIXME: ugly...
             *
             * We treat missing keys as null nodes; this "works" because in
             * the modifiedMembers map, values are JsonMergePatch instances:
             *
             * * if it is a NonObjectMergePatch, the value is replaced
             *   unconditionally;
             * * if it is an ObjectMergePatch, we get back here; the value will
             *   be replaced with a JSON Object anyway before being processed.
             */
            final JsonNode jsonNode = ret.get(key);
            value = jsonNode != null ? jsonNode : NullNode.getInstance();
            ret.replace(key, entry.getValue().apply(value));
        }

        ret.remove(removedMembers);

        return ret;
    }

    @Override
    public void serialize(final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
        jgen.writeStartObject();

        /*
         * Write removed members as JSON nulls
         */
        for (final String member : removedMembers) {
            jgen.writeNullField(member);
        }

        /*
         * Write modified members; delegate to serialization for writing values
         */
        for (final Map.Entry<String, JsonMergePatch> entry : modifiedMembers.entrySet()) {
            jgen.writeFieldName(entry.getKey());
            entry.getValue().serialize(jgen, provider);
        }

        jgen.writeEndObject();
    }

    @Override
    public void serializeWithType(final JsonGenerator jgen,
                                  final SerializerProvider provider,
                                  final TypeSerializer typeSer) throws IOException {

        serialize(jgen, provider);
    }
}
