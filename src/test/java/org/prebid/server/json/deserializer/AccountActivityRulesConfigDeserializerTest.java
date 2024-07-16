package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class AccountActivityRulesConfigDeserializerTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    private AccountActivityRulesConfigDeserializer target;

    @Mock(strictness = LENIENT)
    private JsonParser parser;
    @Mock
    private DeserializationContext context;
    @Mock
    private ObjectCodec codec;

    @BeforeEach
    public void setUp() {
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
