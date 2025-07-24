package org.prebid.server.hooks.modules.rule.engine.core.rules.schema.functions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionArguments;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.random.RandomGenerator;

@RequiredArgsConstructor
public class PercentFunction<T> implements SchemaFunction<T> {

    public static final String NAME = "percent";

    private static final String PCT_FIELD = "pct";

    private final RandomGenerator random;

    @Override
    public String extract(SchemaFunctionArguments<T> arguments) {
        final int resolvedUpperBound = Math.min(Math.max(arguments.getConfig().get(PCT_FIELD).asInt(), 0), 100);
        return Boolean.toString(random.nextInt(100) < resolvedUpperBound);
    }

    @Override
    public void validateConfig(ObjectNode config) {
        ValidationUtils.assertInteger(config, PCT_FIELD);
    }
}
