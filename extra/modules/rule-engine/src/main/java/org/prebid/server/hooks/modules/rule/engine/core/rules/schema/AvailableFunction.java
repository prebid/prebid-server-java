package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.util.ValidationUtils;

import java.util.List;
import java.util.function.Function;

@Value(staticConstructor = "of")
public class AvailableFunction<T> implements SchemaFunction<T> {

    Function<T, Boolean> isAvailable;

    @Override
    public String extract(SchemaFunctionArguments<T> arguments) {
        return Boolean.toString(isAvailable.apply(arguments.getOperand()));
    }

    @Override
    public void validateConfigArguments(List<JsonNode> configArguments) {
        ValidationUtils.assertNoArgs(configArguments);
    }
}
