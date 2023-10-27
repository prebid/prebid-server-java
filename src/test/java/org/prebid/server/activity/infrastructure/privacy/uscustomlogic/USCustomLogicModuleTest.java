package org.prebid.server.activity.infrastructure.privacy.uscustomlogic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicNode;
import io.github.jamsesso.jsonlogic.ast.JsonLogicString;
import io.github.jamsesso.jsonlogic.evaluator.JsonLogicEvaluationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.infrastructure.rule.Rule;
import org.prebid.server.json.JsonLogic;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;

public class USCustomLogicModuleTest extends VertxTest {

    @org.junit.Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private USCustomLogicDataSupplier dataSupplier;

    @Mock
    private JsonLogic jsonLogic;

    @Test
    public void proceedShouldReturnDisallow() throws JsonLogicEvaluationException {
        // given
        final JsonLogicNode jsonLogicNode = new JsonLogicString(StringUtils.EMPTY);
        final Map<String, Object> data = new HashMap<>();

        given(dataSupplier.get()).willReturn(data);
        given(jsonLogic.evaluate(same(jsonLogicNode), same(data))).willReturn(true);

        final USCustomLogicModule target = new USCustomLogicModule(jsonLogic, jsonLogicNode, dataSupplier);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.DISALLOW);
    }

    @Test
    public void proceedShouldReturnAllow() throws JsonLogicEvaluationException {
        // given
        final JsonLogicNode jsonLogicNode = new JsonLogicString(StringUtils.EMPTY);
        final Map<String, Object> data = new HashMap<>();

        given(dataSupplier.get()).willReturn(data);
        given(jsonLogic.evaluate(same(jsonLogicNode), same(data))).willReturn(false);

        final USCustomLogicModule target = new USCustomLogicModule(jsonLogic, jsonLogicNode, dataSupplier);

        // when
        final Rule.Result result = target.proceed(null);

        // then
        assertThat(result).isEqualTo(Rule.Result.ALLOW);
    }

    @Test
    public void asLogEntryShouldReturnExpectedResult() throws JsonLogicEvaluationException {
        // given
        final JsonLogicNode jsonLogicNode = new JsonLogicString(StringUtils.EMPTY);
        final Map<String, Object> data = new HashMap<>();

        given(dataSupplier.get()).willReturn(data);
        given(jsonLogic.evaluate(same(jsonLogicNode), same(data))).willReturn(false);

        final USCustomLogicModule target = new USCustomLogicModule(jsonLogic, jsonLogicNode, dataSupplier);

        // when
        final JsonNode logEntry = target.asLogEntry(mapper);

        // then
        assertThat(logEntry).isEqualTo(TextNode.valueOf("USCustomLogicModule. Precomputed result: ALLOW."));
    }
}
