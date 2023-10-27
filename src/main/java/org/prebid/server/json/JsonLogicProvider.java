package org.prebid.server.json;

import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluator;
import io.github.jamsesso.jsonlogic.evaluator.expressions.AllExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.ArrayHasExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.ConcatenateExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.EqualityExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.FilterExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.IfExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.InExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.InequalityExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.LogExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.LogicExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.MapExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.MathExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.MergeExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.MissingExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.NotExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.NumericComparisonExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.ReduceExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.StrictEqualityExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.StrictInequalityExpression;
import io.github.jamsesso.jsonlogic.evaluator.expressions.SubstringExpression;

import java.util.List;

public class JsonLogicProvider {

    private static final JsonLogic JSON_LOGIC;

    static {
        final JsonLogicEvaluator evaluator = new JsonLogicEvaluator(List.of(
                MathExpression.ADD,
                MathExpression.SUBTRACT,
                MathExpression.MULTIPLY,
                MathExpression.DIVIDE,
                MathExpression.MODULO,
                MathExpression.MIN,
                MathExpression.MAX,
                NumericComparisonExpression.GT,
                NumericComparisonExpression.GTE,
                NumericComparisonExpression.LT,
                NumericComparisonExpression.LTE,
                IfExpression.IF,
                IfExpression.TERNARY,
                EqualityExpression.INSTANCE,
                InequalityExpression.INSTANCE,
                StrictEqualityExpression.INSTANCE,
                StrictInequalityExpression.INSTANCE,
                NotExpression.SINGLE,
                NotExpression.DOUBLE,
                LogicExpression.AND,
                LogicExpression.OR,
                LogExpression.STDOUT,
                MapExpression.INSTANCE,
                FilterExpression.INSTANCE,
                ReduceExpression.INSTANCE,
                AllExpression.INSTANCE,
                ArrayHasExpression.SOME,
                ArrayHasExpression.NONE,
                MergeExpression.INSTANCE,
                InExpression.INSTANCE,
                ConcatenateExpression.INSTANCE,
                SubstringExpression.INSTANCE,
                MissingExpression.ALL,
                MissingExpression.SOME));

        JSON_LOGIC = new JsonLogic(evaluator);
    }

    private JsonLogicProvider() {
    }

    public static JsonLogic jsonLogic() {
        return JSON_LOGIC;
    }
}
