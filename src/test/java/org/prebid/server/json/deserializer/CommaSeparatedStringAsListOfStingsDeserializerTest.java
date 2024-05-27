package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

public class CommaSeparatedStringAsListOfStingsDeserializerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private CommaSeparatedStringAsListOfStringsDeserializer target;

    @Mock
    private JsonParser parser;

    @Mock
    private DeserializationContext context;

    @Before
    public void setUp() {
        target = new CommaSeparatedStringAsListOfStringsDeserializer();
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
                        eq(JsonToken.VALUE_STRING),
                        eq("""
                                Failed to parse field FIELD to List<String> type with a reason: \
                                Expected comma-separated string."""));

        // when and then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> target.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldThrowExceptionOnNullValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn(null);
        given(parser.getCurrentName()).willReturn("FIELD");
        doThrow(RuntimeException.class)
                .when(context)
                .reportWrongTokenException(
                        eq(JsonToken.class),
                        eq(JsonToken.VALUE_STRING),
                        eq("""
                                Failed to parse field FIELD to List<String> type with a reason: \
                                Expected comma-separated string."""));

        // when and then
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> target.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldReturnExpectedValueOnEmptyString() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn("");

        // when
        final List<String> result = target.deserialize(parser, context);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void deserializeShouldReturnExpectedValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn("aa, ab,ac ,ad");

        // when
        final List<String> result = target.deserialize(parser, context);

        // then
        assertThat(result).containsExactly("aa", "ab", "ac", "ad");
    }
}
