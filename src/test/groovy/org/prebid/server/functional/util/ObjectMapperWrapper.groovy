package org.prebid.server.functional.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

trait ObjectMapperWrapper {

    private static final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(NON_NULL)
                                                                 .registerModule(new ZonedDateTimeModule())
    private static final YAMLMapper yaml = new YAMLMapper().setSerializationInclusion(NON_NULL)
                                                                 .registerModule(new ZonedDateTimeModule()) as YAMLMapper
    private static final XmlMapper xmlMapper = new XmlMapper()

    final static String encode(Object object) {
        mapper.writeValueAsString(object)
    }

    final static <T> T decode(String jsonString, Class<T> clazz) {
        mapper.readValue(jsonString, clazz)
    }

    final static <T> T decode(InputStream inputStream, Class<T> clazz) {
        mapper.readValue(inputStream, clazz)
    }

    final static <T> T decode(String jsonString, TypeReference<T> typeReference) {
        mapper.readValue(jsonString, typeReference)
    }

    final static <T> T decodeWithBase64(String base64String, Class<T> clazz) {
        mapper.readValue(new String(Base64.decoder.decode(base64String)), clazz)
    }

    final static Map<String, String> toMap(Object object) {
        mapper.convertValue(object, Map)
    }

    final static JsonNode toJsonNode(String jsonString) {
        mapper.readTree(jsonString)
    }

    final static String encodeXml(Object object) {
        xmlMapper.writeValueAsString(object)
    }

    final static String encodeYaml(Object object) {
        yaml.writeValueAsString(object)
    }

    final static <T> T decodeYaml(String yamlString, Class<T> clazz) {
        yaml.readValue(yamlString, clazz)
    }

    final static <T> T decodeYaml(String yamlString, TypeReference<T> typeReference) {
        yaml.readValue(yamlString, typeReference)
    }

    final static <T> T decodeYaml(InputStream inputStream, Class<T> clazz) {
        yaml.readValue(inputStream, clazz)
    }
}
