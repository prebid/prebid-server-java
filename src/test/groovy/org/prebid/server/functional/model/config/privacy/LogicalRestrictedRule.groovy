package org.prebid.server.functional.model.config.privacy

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = false, ignoreNulls = true)
class LogicalRestrictedRule {

    @JsonProperty("==")
    EqualityValueRule equalityRule

    @JsonProperty("!=")
    InequalityValueRule inequalityRule

    List<LogicalRestrictedRule> or
    List<LogicalRestrictedRule> and

    LogicalRestrictedRule(ValueRestrictedRule valueOperation) {
        if (valueOperation instanceof EqualityValueRule) {
            equalityRule = valueOperation
        } else if (valueOperation instanceof InequalityValueRule) {
            inequalityRule = valueOperation
        }
    }

    private LogicalRestrictedRule() {
    }

    static getRootLogicalRestricted() {
        new LogicalRestrictedRule()
    }

    static LogicalRestrictedRule generateSingleRestrictedRule(LogicalOperation logicalOperator, List<ValueRestrictedRule> valueOperations) {
        valueOperations.inject(new LogicalRestrictedRule()) { logicalRestrictedRule, value ->
            logicalRestrictedRule.includeSubRestriction(logicalOperator, value)
            logicalRestrictedRule
        }
    }

    LogicalRestrictedRule includeSubRestriction(LogicalOperation logicalOperation, LogicalRestrictedRule logicalRestrictedRule) {
        if (logicalOperation == LogicalOperation.OR) {
            or = (or ?: []) + logicalRestrictedRule
        } else if (logicalOperation == LogicalOperation.AND) {
            and = (and ?: []) + logicalRestrictedRule
        }
        this
    }

    LogicalRestrictedRule includeSubRestriction(LogicalOperation logicalOperation, ValueRestrictedRule valueOperation) {
        includeSubRestriction(logicalOperation, new LogicalRestrictedRule(valueOperation))
    }
}
