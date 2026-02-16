package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import groovy.transform.ToString
import org.prebid.server.functional.model.privacy.gpp.GppDataActivity

@ToString(includeNames = true, ignoreNulls = true)
@JsonSerialize(using = ValueRestrictedRuleSerializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class ValueRestrictedRule {

    protected UsNationalPrivacySection privacySection
    protected GppDataActivity value

    protected static final String JSON_LOGIC_VALUE_FIELD = "var"

    ValueRestrictedRule(UsNationalPrivacySection privacySection, GppDataActivity value) {
        this.privacySection = privacySection
        this.value = value
    }

    static class ValueRestrictedRuleSerializer extends JsonSerializer<ValueRestrictedRule> {

        @Override
        void serialize(ValueRestrictedRule valueRestrictedRule, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeStartArray()
            jsonGenerator.writeStartObject()
            jsonGenerator.writeStringField(JSON_LOGIC_VALUE_FIELD, valueRestrictedRule.privacySection.value)
            jsonGenerator.writeEndObject()
            jsonGenerator.writeObject(valueRestrictedRule.value.value)
            jsonGenerator.writeEndArray()
        }
    }
}
