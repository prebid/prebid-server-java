package org.prebid.server.hooks.modules.rule.engine.core.rules;


import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;

import java.util.List;
import java.util.Objects;

public class Rule<FROM, TO, CONTEXT> {

    private final RuleTree<FROM, TO> ruleTree;
    private final List<SchemaFunction<CONTEXT>> schemaFunctions;

    public Rule(RuleTree<FROM, TO> ruleTree, List<SchemaFunction<CONTEXT>> schemaFunctions) {
        this.ruleTree = Objects.requireNonNull(ruleTree);
        this.schemaFunctions = Objects.requireNonNull(schemaFunctions);
    }

    public TO apply(FROM value, CONTEXT context) {
        final List<String> args = schemaFunctions.stream()
                .map(extractor -> extractor.extract(context))
                .toList();

        return ruleTree.getAction(args).apply(context);
    }
}
