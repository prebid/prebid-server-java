package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue
import org.apache.commons.lang3.StringUtils

import java.lang.reflect.Modifier

class Rule {

    private static final String DEFAULT_DELIMITER = "|"

    private String delimiter
    private String siteDomain
    private String pubDomain
    private String domain
    private String bundle
    private String channel
    private MediaType mediaType
    private String size
    private String gptSlot
    private String pbAdSlot
    private Country country
    private DeviceType deviceType

    // TODO add factory for delimiter
    @JsonValue
    String getRule() {
        delimiter = delimiter ?: DEFAULT_DELIMITER
        def stringBuilder = new StringBuilder()
        this.class.declaredFields.findAll { !it.synthetic && !Modifier.isStatic(it.modifiers) && this[it.name] != null && it.name != "delimiter" }.each {
            stringBuilder.append(this[it.name])
                         .append(delimiter)
        }
        StringUtils.removeEnd(stringBuilder.toString(), delimiter).toLowerCase()
    }

    @JsonValue
    String getRule(List<PriceFloorField> fields) {
        delimiter = delimiter ?: DEFAULT_DELIMITER
        def result = ""
        def classFields = this.class.declaredFields.findAll { !it.synthetic }
        fields.each {
            def fieldName = it.value
            def classField = classFields.findAll { it.name == fieldName }
                                        .collect { this[it.name] }
            result += classField[0]
            result += delimiter
        }
        StringUtils.removeEnd(result, delimiter).toLowerCase()
    }
}
