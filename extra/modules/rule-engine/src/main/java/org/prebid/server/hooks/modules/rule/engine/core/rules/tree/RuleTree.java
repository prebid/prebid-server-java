package org.prebid.server.hooks.modules.rule.engine.core.rules.tree;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

import java.util.List;
import java.util.Objects;

public class RuleTree<T> {

    private final RuleNode<T> root;

    @Getter
    private final int depth;

    public RuleTree(RuleNode<T> root, int depth) {
        this.root = Objects.requireNonNull(root);
        this.depth = depth;
    }

    public T getValue(List<String> path) {
        RuleNode<T> next = root;

        for (String pathPart : path) {
            next = switch (next) {
                case RuleNode.LeafNode<T> ignored -> throw new IllegalArgumentException("Argument count mismatch");
                case RuleNode.IntermediateNode<T> node -> ObjectUtils.firstNonNull(node.next(pathPart), node.next("*"));
                case null -> throw new NoMatchingRuleException();
            };
        }

        return switch (next) {
            case RuleNode.LeafNode<T> leaf -> leaf.value();
            case RuleNode.IntermediateNode<T> ignored -> throw new IllegalArgumentException("Argument count mismatch");
            case null -> throw new NoMatchingRuleException();
        };
    }
}
