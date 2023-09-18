package org.prebid.server.functional.model.config

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonDeserialize(using = EqualityValueRuleDeserialize.class)
class EqualityValueRule extends ValueRestrictedRule{

    EqualityValueRule(UsNationalPrivacySection privacySection, DataActivity value) {
        super(privacySection, value)
    }

    static class EqualityValueRuleDeserialize extends JsonDeserializer<EqualityValueRule> {

        @Override
        EqualityValueRule deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser)
            def privacySection = UsNationalPrivacySection.valueFromText(node.get(0).get(JSON_LOGIC_VALUE_FIELD).textValue())
            def value = DataActivity.fromInt(node.get(1).asInt())
            return new EqualityValueRule(privacySection, value)
        }
    }
}
