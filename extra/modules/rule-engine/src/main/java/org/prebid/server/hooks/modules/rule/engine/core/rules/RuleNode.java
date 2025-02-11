package org.prebid.server.hooks.modules.rule.engine.core.rules;

import java.util.Map;
import java.util.function.Function;

public sealed interface RuleNode<T, R> {

    record IntermediateNode<T, R>(Map<String, RuleNode<T, R>> children) implements RuleNode<T, R> {

        public RuleNode<T, R> next(String arg) {
            return children.get(arg);
        }
    }

    record LeafNode<T, R>(Function<T, R> action) implements RuleNode<T, R> {
    }
}
