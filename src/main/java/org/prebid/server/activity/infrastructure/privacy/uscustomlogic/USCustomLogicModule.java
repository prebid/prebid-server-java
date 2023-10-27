package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.privacy.PrivacyModule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JsonLogic;

import java.util.Map;
import java.util.function.Supplier;

public class USCustomLogicModule implements PrivacyModule {

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
    public Result proceed(ActivityInvocationPayload activityInvocationPayload) {
        return result;
    }
}
