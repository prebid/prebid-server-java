package core.rules.tree;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.rule.engine.core.rules.exception.NoMatchingRuleException;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleNode;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

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
        assertThat(tree.getValue(asList("A", "B"))).isEqualTo("AB");
        assertThat(tree.getValue(asList("A", "C"))).isEqualTo("AC");
        assertThat(tree.getValue(asList("B", "B"))).isEqualTo("BB");
        assertThat(tree.getValue(asList("B", "C"))).isEqualTo("BC");
        assertThatExceptionOfType(NoMatchingRuleException.class).isThrownBy(() -> tree.getValue(asList("C", "B")));
        assertThatExceptionOfType(NoMatchingRuleException.class).isThrownBy(() -> tree.getValue(singletonList("C")));
    }
}
