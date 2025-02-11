package org.prebid.server.hooks.modules.rule.engine.core.rules;


import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class Rule<T, U, R> {

    private final RuleTree<T, U> ruleTree;
    private final List<ArgumentExtractor<R>> argumentExtractors;

    public Rule(RuleTree<T, U> ruleTree, List<ArgumentExtractor<R>> argumentExtractors) {
        this.ruleTree = Objects.requireNonNull(ruleTree);
        this.argumentExtractors = Objects.requireNonNull(argumentExtractors);
    }

    public Function<T, U> apply(R value) {
        final List<String> args = argumentExtractors.stream()
                .map(extractor -> extractor.extract(value))
                .toList();

        return ruleTree.getAction(args);
    }
}
