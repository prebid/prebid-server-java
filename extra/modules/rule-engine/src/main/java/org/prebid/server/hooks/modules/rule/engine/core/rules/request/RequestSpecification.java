package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidSchemaFunctionException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.schema.functions.RequestSchemaFunctions;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestSpecification implements StageSpecification<RequestContext, BidRequest> {

    public static final RequestSpecification INSTANCE = new RequestSpecification();

    public static final Set<String> PER_IMP_SCHEMA_FUNCTIONS = Set.of();

    private static final Map<String, SchemaFunction<RequestContext>> SCHEMA_FUNCTIONS = Map.of(
            RequestSchemaFunctions.DEVICE_COUNTRY_FUNCTION_NAME, RequestSchemaFunctions.DEVICE_COUNTRY_FUNCTION,
            RequestSchemaFunctions.DEVICE_COUNTRY_IN_FUNCTION_NAME, RequestSchemaFunctions.DEVICE_COUNTRY_IN_FUNCTION);

    private static final Map<String, ResultFunction<BidRequest>> RESULT_FUNCTIONS = Map.of();

    public SchemaFunction<RequestContext> schemaFunctionByName(String name) {
        final SchemaFunction<RequestContext> function = SCHEMA_FUNCTIONS.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }

    public ResultFunction<BidRequest> resultFunctionByName(String name) {
        final ResultFunction<BidRequest> function = RESULT_FUNCTIONS.get(name);
        if (function == null) {
            throw new InvalidSchemaFunctionException(name);
        }

        return function;
    }
}
