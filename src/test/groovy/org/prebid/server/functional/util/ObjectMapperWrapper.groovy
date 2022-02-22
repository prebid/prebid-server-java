package org.prebid.server.functional.util

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

// TODO make this into a Trait so that we won't need to pass it around. This will allow us to use it in the models
class ObjectMapperWrapper {

    private final ObjectMapper mapper
    private final XmlMapper xmlMapper

    ObjectMapperWrapper() {
        mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
                                   .registerModule(new JavaTimeModule())
        xmlMapper = new XmlMapper()
    }

    String encode(Object object) {
        mapper.writeValueAsString(object)
    }

    def <T> T decode(JsonNode jsonNode, Class<T> clazz) {
        mapper.treeToValue(jsonNode, clazz)
    }

    def <T> T decode(String jsonString, Class<T> clazz) {
        mapper.readValue(jsonString, clazz)
    }

    def <T> T decode(String jsonString, TypeReference<T> typeReference) {
        mapper.readValue(jsonString, typeReference)
    }

    Map<String, String> toMap(Object object) {
        mapper.convertValue(object, Map)
    }

    JsonNode toJsonNode(String jsonString) {
        mapper.readTree(jsonString)
    }

    String encodeXml(Object object) {
        xmlMapper.writeValueAsString(object)
    }
}
