package org.prebid.server.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.io.IOException;

public class BidTypeOrdinalSerializer extends StdSerializer<BidType> {

    public BidTypeOrdinalSerializer() {
        super(BidType.class);
    }

    @Override
    public void serialize(BidType value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value != null) {
            gen.writeObject(value.ordinal() + 1);
        }
    }
}
