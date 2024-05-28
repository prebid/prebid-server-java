package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

public class AccountActivityRulesConfigDeserializerTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private AccountActivityRulesConfigDeserializer target;

    @Mock
    private JsonParser parser;
    @Mock
    private DeserializationContext context;
    @Mock
    private ObjectCodec codec;

    @Before
    public void setUp() throws IOException {
        target = new AccountActivityRulesConfigDeserializer();
    }

    @Test
    public void deserializeShouldThrowExceptionOnWrongJsonToken() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_FALSE);
        given(parser.getCurrentName()).willReturn("FIELD");
        doThrow(RuntimeException.class)
                .when(context)
                .reportWrongTokenException(
                        eq(JsonToken.class),
                        eq(JsonToken.START_ARRAY),
                        eq("Failed to parse field FIELD to array with a reason: Expected array."));

        // when and then
        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> target.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldReturnExpectedValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.START_ARRAY);
        given(parser.getCodec()).willReturn(codec);

        final ArrayNode arrayNode = mapper.createArrayNode();
        arrayNode.addObject();
        arrayNode.addObject();
        given(codec.readTree(same(parser))).willReturn(arrayNode);

        // when
        final List<? extends AccountActivityRuleConfig> result = target.deserialize(parser, context);

        // then
        assertThat(result).hasSize(2);
    }
}
