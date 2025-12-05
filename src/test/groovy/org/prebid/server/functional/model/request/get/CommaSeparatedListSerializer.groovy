package org.prebid.server.functional.model.request.get

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class CommaSeparatedListSerializer extends JsonSerializer<List<Object>> {

    @Override
    void serialize(List<Object> value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        String result = value.join(',')
        generator.writeString(result)
    }
}
