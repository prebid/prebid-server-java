package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class IntegerFlagDeserializerTest {

    private IntegerFlagDeserializer integerFlagDeserializer;

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext deserializationContext;

    @BeforeEach
    public void setUp() {
        integerFlagDeserializer = new IntegerFlagDeserializer();
    }

    @Test
    public void deserializeShouldReturnOneWhenFieldIsBooleanAndHasValueTrue() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_TRUE);

        // when
        final Integer result = integerFlagDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void deserializeShouldReturnZeroWhenFieldIsBooleanAndHasValueFalse() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_FALSE);

        // when
        final Integer result = integerFlagDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(0);
    }

    @Test
    public void deserializeShouldReturnIntegerValueWhenFieldIsInt() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(1);

        // when
        final Integer result = integerFlagDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void deserializeShouldThrowJsonParsingExceptionWhenTypeIsNotBooleanOrInt() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        doThrow(MismatchedInputException.from(jsonParser, Integer.class, "errorMessage"))
                .when(deserializationContext)
                .reportWrongTokenException(eq(JsonToken.class), eq(JsonToken.VALUE_NUMBER_INT), anyString());

        // when and then
        assertThatThrownBy(() -> integerFlagDeserializer.deserialize(jsonParser, deserializationContext))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessage("errorMessage");
    }
}
