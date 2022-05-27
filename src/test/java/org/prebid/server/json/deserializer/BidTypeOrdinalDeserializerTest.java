package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

public class BidTypeOrdinalDeserializerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private JsonParser jsonParser;

    @Mock
    private DeserializationContext deserializationContext;

    private BidTypeOrdinalDeserializer bidTypeOrdinalDeserializer;

    @Before
    public void setUp() {
        bidTypeOrdinalDeserializer = new BidTypeOrdinalDeserializer();
    }

    @Test
    public void deserializeShouldReturnNullBidTypeForNull() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NULL);

        // when
        final BidType result = bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void deserializeShouldReturnBannerBidTypeFor1() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(1);

        // when
        final BidType result = bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(BidType.banner);
    }

    @Test
    public void deserializeShouldReturnVideoBidTypeFor2() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(2);

        // when
        final BidType result = bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(BidType.video);
    }

    @Test
    public void deserializeShouldReturnAudioBidTypeFor3() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(3);

        // when
        final BidType result = bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(BidType.audio);
    }

    @Test
    public void deserializeShouldReturnNativeBidTypeFor4() throws IOException {
        // given
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(4);

        // when
        final BidType result = bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext);

        // then
        assertThat(result).isEqualTo(BidType.xNative);
    }

    @Test
    public void deserializeShouldThrowExceptionForInvalidInteger() throws IOException {
        // given
        given(jsonParser.getCurrentName()).willReturn("someName");
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_NUMBER_INT);
        given(jsonParser.getValueAsInt()).willReturn(5);
        doThrow(MismatchedInputException.from(jsonParser, Integer.class, "errorMessage"))
                .when(deserializationContext)
                .reportPropertyInputMismatch(eq(Integer.class), eq("someName"), anyString(), anyInt());

        // when and then
        assertThatThrownBy(() -> bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessage("errorMessage");
    }

    @Test
    public void deserializeShouldThrowExceptionForInvalidJsonToken() throws IOException {
        // given
        given(jsonParser.getCurrentName()).willReturn("someName");
        given(jsonParser.getCurrentToken()).willReturn(JsonToken.VALUE_STRING);
        doThrow(MismatchedInputException.from(jsonParser, Integer.class, "errorMessage"))
                .when(deserializationContext)
                .reportPropertyInputMismatch(eq(Integer.class), eq("someName"), anyString(), anyInt());

        // when and then
        assertThatThrownBy(() -> bidTypeOrdinalDeserializer.deserialize(jsonParser, deserializationContext))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessage("errorMessage");
    }
}
