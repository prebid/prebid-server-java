package org.prebid.server.hooks.modules.rule.engine.core.rules.tree;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class RuleTreeTest {

    @Test
    public void getValueShouldReturnExpectedValue() {
        // given
        final Map<String, RuleNode<String>> subnodes = Map.of(
                "A",
                new RuleNode.IntermediateNode<>(
                        Map.of("B", new RuleNode.LeafNode<>("AB"), "*", new RuleNode.LeafNode<>("AC"))),
                "B",
                new RuleNode.IntermediateNode<>(
                        Map.of("B", new RuleNode.LeafNode<>("BB"), "C", new RuleNode.LeafNode<>("BC"))));

        final RuleTree<String> tree = new RuleTree<>(new RuleNode.IntermediateNode<>(subnodes), 2);

        // when and then
        assertThat(tree.lookup(asList("A", "B"))).isEqualTo(LookupResult.of("AB", List.of("A", "B")));
        assertThat(tree.lookup(asList("A", "C"))).isEqualTo(LookupResult.of("AC", List.of("A", "*")));
        assertThat(tree.lookup(asList("B", "B"))).isEqualTo(LookupResult.of("BB", List.of("B", "B")));
        assertThat(tree.lookup(asList("B", "C"))).isEqualTo(LookupResult.of("BC", List.of("B", "C")));
        assertThatExceptionOfType(NoMatchingRuleException.class).isThrownBy(() -> tree.lookup(asList("C", "B")));
        assertThatExceptionOfType(NoMatchingRuleException.class).isThrownBy(() -> tree.lookup(singletonList("C")));
    }
}
