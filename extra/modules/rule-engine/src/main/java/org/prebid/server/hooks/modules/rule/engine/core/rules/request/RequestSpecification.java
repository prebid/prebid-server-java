package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.prebid.server.hooks.modules.rule.engine.core.rules.StageSpecification;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.InvalidSchemaFunctionException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.result.functions.IncludeBiddersFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.schema.functions.DeviceCountryFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.ResultFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestSpecification implements StageSpecification<RequestPayload, BidRequest> {

    public static final RequestSpecification INSTANCE = new RequestSpecification();

    public static final Set<String> PER_IMP_SCHEMA_FUNCTIONS = Set.of();

    private static final Map<String, SchemaFunction<RequestPayload>> SCHEMA_FUNCTIONS = Map.of(
            DeviceCountryFunction.NAME, DeviceCountryFunction.INSTANCE);

    private static final Map<String, ResultFunction<BidRequest>> RESULT_FUNCTIONS = Map.of(
            IncludeBiddersFunction.NAME, IncludeBiddersFunction.INSTANCE);

    public SchemaFunction<RequestPayload> schemaFunctionByName(String name) {
        final SchemaFunction<RequestPayload> function = SCHEMA_FUNCTIONS.get(name);
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
