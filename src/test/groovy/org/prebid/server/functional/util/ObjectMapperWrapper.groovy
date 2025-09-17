package org.prebid.server.functional.util

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
<<<<<<< HEAD
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
=======
>>>>>>> 04d9d4a13 (Initial commit)

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL

trait ObjectMapperWrapper {

    private static final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(NON_NULL)
                                                                 .registerModule(new ZonedDateTimeModule())
<<<<<<< HEAD
    private static final YAMLMapper yamlMapper = new YAMLMapper().setSerializationInclusion(NON_NULL) as YAMLMapper
=======
>>>>>>> 04d9d4a13 (Initial commit)
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
<<<<<<< HEAD

    final static String encodeYaml(Object object) {
        yamlMapper.writeValueAsString(object)
    }
=======
>>>>>>> 04d9d4a13 (Initial commit)
}
