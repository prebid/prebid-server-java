package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.mchange.util.AssertException;
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
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

public class CommaSeparatedStringAsListOfIntegersDeserializerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private CommaSeparatedStringAsListOfIntegersDeserializer commaSeparatedStringAsListOfIntegersDeserializer;

    @Mock
    private JsonParser parser;

    @Mock
    private DeserializationContext context;

    @Before
    public void setUp() {
        commaSeparatedStringAsListOfIntegersDeserializer = new CommaSeparatedStringAsListOfIntegersDeserializer();
    }

    @Test
    public void deserializeShouldThrowExceptionOnWrongJsonToken() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_FALSE);
        given(parser.getCurrentName()).willReturn("FIELD");
        doThrow(AssertException.class)
                .when(context)
                .reportWrongTokenException(
                        eq(JsonToken.class),
                        eq(JsonToken.VALUE_STRING),
                        eq("""
                                Failed to parse field FIELD to List<Integer> type with a reason: \
                                Expected comma-separated string."""));

        // when and then
        assertThatExceptionOfType(AssertException.class)
                .isThrownBy(() -> commaSeparatedStringAsListOfIntegersDeserializer.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldThrowExceptionOnNullValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn(null);
        given(parser.getCurrentName()).willReturn("FIELD");
        doThrow(AssertException.class)
                .when(context)
                .reportWrongTokenException(
                        eq(JsonToken.class),
                        eq(JsonToken.VALUE_STRING),
                        eq("""
                                Failed to parse field FIELD to List<Integer> type with a reason: \
                                Expected comma-separated string."""));

        // when and then
        assertThatExceptionOfType(AssertException.class)
                .isThrownBy(() -> commaSeparatedStringAsListOfIntegersDeserializer.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldThrowExceptionOnInvalidValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn("invalid");
        given(parser.getCurrentName()).willReturn("FIELD");
        doThrow(AssertException.class)
                .when(context)
                .reportPropertyInputMismatch(
                        eq(JsonToken.class),
                        eq("FIELD"),
                        matches("""
                                Failed to parse field FIELD to List<Integer> type with a reason: \
                                NumberFormatException"""));

        // when and then
        assertThatExceptionOfType(AssertException.class)
                .isThrownBy(() -> commaSeparatedStringAsListOfIntegersDeserializer.deserialize(parser, context));
    }

    @Test
    public void deserializeShouldReturnExpectedValueOnEmptyString() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn("");

        // when
        final List<Integer> result = commaSeparatedStringAsListOfIntegersDeserializer.deserialize(parser, context);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void deserializeShouldReturnExpectedValue() throws IOException {
        // given
        given(parser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        given(parser.getValueAsString()).willReturn("1, 2,3 ,4");

        // when
        final List<Integer> result = commaSeparatedStringAsListOfIntegersDeserializer.deserialize(parser, context);

        // then
        assertThat(result).containsExactly(1, 2, 3, 4);
    }
}
