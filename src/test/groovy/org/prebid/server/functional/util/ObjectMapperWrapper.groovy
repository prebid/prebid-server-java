package org.prebid.server.functional.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

trait ObjectMapperWrapper {

    private static final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(NON_NULL)
                                                                 .registerModule(new JavaTimeModule())
    private static final XmlMapper xmlMapper = new XmlMapper()

    final static String encode(Object object) {
        mapper.writeValueAsString(object)
    }

    final <T> T decode(String jsonString, Class<T> clazz) {
        mapper.readValue(jsonString, clazz)
    }

    final <T> T decode(String jsonString, TypeReference<T> typeReference) {
        mapper.readValue(jsonString, typeReference)
    }

    final Map<String, String> toMap(Object object) {
        mapper.convertValue(object, Map)
    }

    final JsonNode toJsonNode(String jsonString) {
        mapper.readTree(jsonString)
    }

    final String encodeXml(Object object) {
        xmlMapper.writeValueAsString(object)
    }
}
