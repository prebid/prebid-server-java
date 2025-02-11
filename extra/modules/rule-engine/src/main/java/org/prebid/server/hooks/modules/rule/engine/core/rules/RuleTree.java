package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class RuleTree<T, R> {

    RuleNode<T, R> root;

    public RuleTree(RuleNode<T, R> root) {
        this.root = Objects.requireNonNull(root);
    }

    public Function<T, R> getAction(List<String> args) {
        RuleNode<T, R> next = root;

        for (String arg : args) {
            next = switch (next) {
                case RuleNode.LeafNode<T, R> ignored -> throw new IllegalArgumentException("Argument count mismatch");
                case RuleNode.IntermediateNode<T, R> node -> ObjectUtils.firstNonNull(node.next(arg), node.next("*"));
                case null -> throw new IllegalArgumentException("Action absent");
            };
        }

        return switch (next) {
            case RuleNode.LeafNode<T, R> leaf -> leaf.action();
            case RuleNode.IntermediateNode<T, R> ignored ->
                    throw new IllegalArgumentException("Argument count mismatch");
            case null -> throw new IllegalArgumentException("Action absent");
        };
    }

}
