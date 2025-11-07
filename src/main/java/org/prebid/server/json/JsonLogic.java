package org.prebid.server.json;

import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicParseException;
import io.github.jamsesso.jsonlogic.ast.JsonLogicParser;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluator;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

public class JsonLogic {

    private final JsonLogicEvaluator evaluator;

    public JsonLogic(JsonLogicEvaluator jsonLogicEvaluator) {
        evaluator = Objects.requireNonNull(jsonLogicEvaluator);
    }

    public JsonLogicNode parse(String jsonNode) {
        try {
            return JsonLogicParser.parse(jsonNode);
        } catch (JsonLogicParseException e) {
            throw new DecodeException(e.getMessage());
        }
    }

    public boolean evaluate(JsonLogicNode jsonLogicNode, Map<String, Object> data) throws JsonLogicEvaluationException {
        final Object result = evaluator.evaluate(jsonLogicNode, data, StringUtils.EMPTY);

        if (result instanceof Boolean booleanResult) {
            return booleanResult;
        }
        throw new JsonLogicEvaluationException("Wrong type was returned.", StringUtils.EMPTY);
    }
}
