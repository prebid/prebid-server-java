package org.prebid.server.hooks.modules.rule.engine.core.rules;

import java.util.Map;
import java.util.function.Function;

public sealed interface RuleNode<T> {

    record IntermediateNode<T>(Map<String, RuleNode<T>> children) implements RuleNode<T> {

        public RuleNode<T> next(String arg) {
            return children.get(arg);
        }
    }

    record LeafNode<T>(T value) implements RuleNode<T> {
    }
}
