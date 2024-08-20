package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.prebid.server.activity.infrastructure.debug.Loggable;
import org.prebid.server.activity.infrastructure.payload.CompositeActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JsonLogic;

import java.util.Map;
import java.util.function.Supplier;

public class USCustomLogicModule implements PrivacyModule, Loggable {

    private final Result result;

    public USCustomLogicModule(JsonLogic jsonLogic,
                               JsonLogicNode jsonLogicNode,
                               Supplier<Map<String, Object>> dataSupplier) {

        try {
            result = jsonLogic.evaluate(jsonLogicNode, dataSupplier.get())
                    ? Result.DISALLOW
                    : Result.ALLOW;
        } catch (JsonLogicEvaluationException e) {
            throw new PreBidException("An error occurred while evaluating the JsonLogic expression: " + e.getMessage());
        }
    }

    @Override
    public Result proceed(CompositeActivityInvocationPayload payload) {
        return result;
    }

    @Override
    public JsonNode asLogEntry(ObjectMapper mapper) {
        return TextNode.valueOf(
                "%s. Precomputed result: %s.".formatted(
                        this.getClass().getSimpleName(),
                        result));
    }
}
