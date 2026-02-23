package org.prebid.server.activity.infrastructure.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.activity.infrastructure.debug.ActivityDebugUtils;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AndRule implements Rule, Loggable {

    private final List<? extends Rule> rules;

    public AndRule(List<? extends Rule> rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    @Override
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        Result result = Result.ABSTAIN;

        for (Rule rule : rules) {
            final Result ruleResult = rule.proceed(activityInvocationPayload);
            if (ruleResult != Result.ABSTAIN) {
                result = ruleResult;
            }

            if (result == Result.DISALLOW) {
                break;
            }
        }

        return result;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        final ObjectNode andNode = mapper.createObjectNode();
        final ArrayNode arrayNode = andNode.putArray("and");
        arrayNode.addAll(ActivityDebugUtils.asLogEntry(rules, mapper));

        return andNode;
    }

    public List<Rule> rules() {
        return Collections.unmodifiableList(rules);
    }
}
