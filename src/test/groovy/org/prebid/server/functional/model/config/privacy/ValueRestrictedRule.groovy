package org.prebid.server.functional.model.config.privacy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonSerialize(using = ValueRestrictedRuleSerializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class ValueRestrictedRule {

    protected UsNationalPrivacySection privacySection
    protected DataActivity value

    protected static final String JSON_LOGIC_VALUE_FIELD = "var"

    ValueRestrictedRule(UsNationalPrivacySection privacySection, DataActivity dataActivity) {
        this.privacySection = privacySection
        this.value = dataActivity
    }

    static class ValueRestrictedRuleSerializer extends JsonSerializer<ValueRestrictedRule> {

        @Override
        void serialize(ValueRestrictedRule valueRestrictedRule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartArray()
            jsonGenerator.writeStartObject()
            jsonGenerator.writeStringField(JSON_LOGIC_VALUE_FIELD, valueRestrictedRule.privacySection.value)
            jsonGenerator.writeEndObject()
            jsonGenerator.writeObject(valueRestrictedRule.value.dataActivityBits)
            jsonGenerator.writeEndArray()
        }
    }
}
