package org.prebid.server.functional.model.config

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import groovy.transform.ToString
import org.prebid.server.functional.model.privacy.gpp.GppDataActivity

@ToString(includeNames = true, ignoreNulls = true)
@JsonDeserialize(using = InequalityValueRuleDeserialize.class)
class InequalityValueRule extends ValueRestrictedRule {

    InequalityValueRule(UsNationalPrivacySection privacySection, GppDataActivity value) {
        super(privacySection, value)
    }

    static class InequalityValueRuleDeserialize extends JsonDeserializer<InequalityValueRule> {

        @Override
        InequalityValueRule deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser)
            def privacySection = UsNationalPrivacySection.valueFromText(node?.get(0)?.get(JSON_LOGIC_VALUE_FIELD)?.textValue())
            def value = GppDataActivity.fromInt(node?.get(1)?.asInt())
            return new InequalityValueRule(privacySection, value)
        }
    }
}
