package org.prebid.server.json.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;

public class BidTypeOrdinalDeserializer extends StdDeserializer<BidType> {

    private final BidType[] possibleBidTypes;

    public BidTypeOrdinalDeserializer() {
        super(BidType.class);

        possibleBidTypes = BidType.values();
    }

    @Override
    public BidType deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        final String fieldName = parser.getCurrentName();

        switch (parser.getCurrentToken()) {
            case VALUE_NUMBER_INT:
                final int value = parser.getValueAsInt();
                if (value < 1 || value > possibleBidTypes.length) {
                    reportPropertyInputMismatch(fieldName, context);
                }

                return possibleBidTypes[value - 1];
            case VALUE_NULL:
                return null;
            default:
                reportPropertyInputMismatch(fieldName, context);
                throw new AssertionError();
        }
    }

    private void reportPropertyInputMismatch(String fieldName, DeserializationContext context)
            throws JsonMappingException {

        context.reportPropertyInputMismatch(
                Integer.class,
                fieldName,
                "Expected integer number from 1 to %d inclusive",
                possibleBidTypes.length);
    }
}
