package org.prebid.server.hooks.modules.rule.engine.core.rules.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.Imp;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class SchemaFunctionArguments<T> {

    T operand;

    List<JsonNode> args;

    boolean validation;

    Imp imp;
}
