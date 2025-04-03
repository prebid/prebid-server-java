package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class RuleTree<T> {

    RuleNode<T> root;

    public RuleTree(RuleNode<T> root) {
        this.root = Objects.requireNonNull(root);
    }

    public T getValue(List<String> path) {
        RuleNode<T> next = root;

        for (String pathPart : path) {
            next = switch (next) {
                case RuleNode.LeafNode<T> ignored -> throw new IllegalArgumentException("Argument count mismatch");
                case RuleNode.IntermediateNode<T> node ->
                        ObjectUtils.firstNonNull(node.next(pathPart), node.next("*"));
                case null -> throw new IllegalArgumentException("Action absent");
            };
        }

        return switch (next) {
            case RuleNode.LeafNode<T> leaf -> leaf.value();
            case RuleNode.IntermediateNode<T> ignored ->
                    throw new IllegalArgumentException("Argument count mismatch");
            case null -> throw new IllegalArgumentException("Action absent");
        };
    }
}
