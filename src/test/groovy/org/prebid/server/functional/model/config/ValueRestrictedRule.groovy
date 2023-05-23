package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonSerialize(using = ValueRestrictedRuleSerializer.class)
@JsonDeserialize(using = ValueRestrictedRuleDeserialize.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class ValueRestrictedRule {

    Boolean shouldBeEqual
    UsNationalPrivacySection privacySection
    Integer value

    private static final String JSON_LOGIC_VALUE_FIELD = "var"

    ValueRestrictedRule(Boolean shouldBeEqual, UsNationalPrivacySection privacySection, Integer value) {
        this.shouldBeEqual = shouldBeEqual
        this.privacySection = privacySection
        this.value = value
    }

    static class ValueRestrictedRuleDeserialize extends JsonDeserializer<ValueRestrictedRule> {

        @Override
        ValueRestrictedRule deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser)
            def privacySection = UsNationalPrivacySection.valueFromText(node.get(0).get(JSON_LOGIC_VALUE_FIELD).textValue())
            def value = node.get(1).asInt()
            return new ValueRestrictedRule(null, privacySection, value)
        }
    }

    static class ValueRestrictedRuleSerializer extends JsonSerializer<ValueRestrictedRule> {

        @Override
        void serialize(ValueRestrictedRule valueRestrictedRule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartArray()
            jsonGenerator.writeStartObject()
            jsonGenerator.writeStringField(JSON_LOGIC_VALUE_FIELD, valueRestrictedRule.privacySection.value)
            jsonGenerator.writeEndObject()
            jsonGenerator.writeObject(valueRestrictedRule.value)
            jsonGenerator.writeEndArray()
        }
    }
}
