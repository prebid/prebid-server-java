package org.prebid.server.hooks.modules.rule.engine.core.rules.tree;

import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RuleTree<T> {

    public static final String WILDCARD_MATCHER = "*";

    private final RuleNode<T> root;

    @Getter
    private final int depth;

    public RuleTree(RuleNode<T> root, int depth) {
        this.root = Objects.requireNonNull(root);
        this.depth = depth;
    }

    public LookupResult<T> lookup(List<String> path) {
        final List<String> matches = new ArrayList<>();
        RuleNode<T> next = root;

        for (String pathPart : path) {
            next = switch (next) {
                case RuleNode.IntermediateNode<T> node -> {
                    final RuleNode<T> result = node.next(pathPart);
                    matches.add(result == null ? WILDCARD_MATCHER : pathPart);
                    yield ObjectUtils.defaultIfNull(result, node.next(WILDCARD_MATCHER));
                }

                case RuleNode.LeafNode<T> ignored -> throw new IllegalArgumentException("Argument count mismatch");
                case null -> throw new NoMatchingRuleException();
            };
        }

        return switch (next) {
            case RuleNode.LeafNode<T> leaf -> LookupResult.of(leaf.value(), matches);
            case RuleNode.IntermediateNode<T> ignored -> throw new IllegalArgumentException("Argument count mismatch");
            case null -> throw new NoMatchingRuleException();
        };
    }
}
