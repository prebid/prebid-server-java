package org.prebid.server.hooks.modules.rule.engine.core.request;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.rule.engine.core.rules.ConditionalRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleConfig;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunction;
import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.SchemaFunctionHolder;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
class RequestConditionalRuleFactoryTest {

    private final RequestConditionalRuleFactory target = new RequestConditionalRuleFactory();

    @Mock(strictness = LENIENT)
    SchemaFunction<BidRequest, RequestRuleContext> schemaFunction;

    @Mock(strictness = LENIENT)
    RuleTree<RuleConfig<BidRequest, RequestRuleContext>> ruleTree;

    @Test
    void createShouldReturnConditionalRuleWhenSchemaDoesNotContainPerImpFunctions() {
        // given
        final Schema<BidRequest, RequestRuleContext> schema = Schema.of(
                Collections.singletonList(SchemaFunctionHolder.of("function", schemaFunction, null)));

        // when and then
        assertThat(target.create(schema, ruleTree, "key", "version"))
                .isInstanceOf(ConditionalRule.class);
    }

    @Test
    void createShouldReturnPerImpConditionalRuleWhenSchemaContainPerImpFunctions() {
        // given
        final String perImpSchemaFunctionName = takeRandomElement(RequestStageSpecification.PER_IMP_SCHEMA_FUNCTIONS);
        final Schema<BidRequest, RequestRuleContext> schema = Schema.of(
                Collections.singletonList(SchemaFunctionHolder.of(perImpSchemaFunctionName, schemaFunction, null)));

        // when and then
        assertThat(target.create(schema, ruleTree, "key", "version"))
                .isInstanceOf(PerImpConditionalRule.class);
    }

    private static <T> T takeRandomElement(Collection<T> collection) {
        return collection
                .stream()
                .skip(ThreadLocalRandom.current().nextInt(collection.size()))
                .findAny()
                .orElse(null);
    }
}
