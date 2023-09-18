package org.prebid.server.functional.model.config

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
        validateDataActivity(privacySection, dataActivity)
        this.privacySection = privacySection
        this.value = dataActivity
    }

    static void validateDataActivity(UsNationalPrivacySection privacySection, DataActivity dataActivity) {
        if (privacySection in [UsNationalPrivacySection.SENSITIVE_DATA_RACIAL_RANDOM,
                               UsNationalPrivacySection.SENSITIVE_DATA_PROCESSING_ALL,
                               UsNationalPrivacySection.CHILD_CONSENTS_FROM_RANDOM,
                               UsNationalPrivacySection.PERSONAL_DATA_CONSENTS]) {
            if (dataActivity == DataActivity.NOTICE_PROVIDED || dataActivity == DataActivity.NOTICE_NOT_PROVIDED) {
                throw new IllegalStateException("$privacySection doesn't support NOTICE_PROVIDED and NOTICE_NOT_PROVIDED types")
            }
        } else if (dataActivity == DataActivity.NO_CONSENT && dataActivity == DataActivity.CONSENT) {
            throw new IllegalStateException("$privacySection doesn't support NO_CONSENT and CONSENT types")
        }
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
