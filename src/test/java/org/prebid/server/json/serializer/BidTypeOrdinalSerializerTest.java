package org.prebid.server.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BidTypeOrdinalSerializerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private JsonGenerator gen;

    @Mock
    private SerializerProvider serializerProvider;

    private BidTypeOrdinalSerializer bidTypeOrdinalSerializer;

    @Before
    public void setUp() {
        bidTypeOrdinalSerializer = new BidTypeOrdinalSerializer();
    }

    @Test
    public void serializeShouldWrite1ForBannerBidType() throws IOException {
        // when
        bidTypeOrdinalSerializer.serialize(BidType.banner, gen, serializerProvider);

        // then
        verify(gen).writeNumber(1);
        verifyNoMoreInteractions(gen);
        verifyNoInteractions(serializerProvider);
    }

    @Test
    public void serializeShouldWrite2ForVideoBidType() throws IOException {
        // when
        bidTypeOrdinalSerializer.serialize(BidType.video, gen, serializerProvider);

        // then
        verify(gen).writeNumber(2);
        verifyNoMoreInteractions(gen);
        verifyNoInteractions(serializerProvider);
    }

    @Test
    public void serializeShouldWrite3ForAudioBidType() throws IOException {
        // when
        bidTypeOrdinalSerializer.serialize(BidType.audio, gen, serializerProvider);

        // then
        verify(gen).writeNumber(3);
        verifyNoMoreInteractions(gen);
        verifyNoInteractions(serializerProvider);
    }

    @Test
    public void serializeShouldWrite4ForNativeBidType() throws IOException {
        // when
        bidTypeOrdinalSerializer.serialize(BidType.xNative, gen, serializerProvider);

        // then
        verify(gen).writeNumber(4);
        verifyNoMoreInteractions(gen);
        verifyNoInteractions(serializerProvider);
    }
}
