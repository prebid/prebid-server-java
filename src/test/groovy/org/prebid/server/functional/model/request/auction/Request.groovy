package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.testcontainers.Dependencies

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Request {

    String ver
    Integer context
    Integer contextsubtype
    Integer plcmttype
    Integer plcmtcnt
    Integer seq
    List<Asset> assets
    Integer aurlsupport
    Integer durlsupport
    List<EventTracker> eventtrackers
    Integer privacy

    static Request getRequest() {
        new Request().tap {
            context = 1
            plcmttype = 1
            it.addAsset(Asset.assetTitle)
            it.addAsset(Asset.assetImg)
            it.addAsset(Asset.assetData)
        }
    }

    void addAsset(Asset asset) {
        if (assets == null) {
            assets = []
        }
        assets.add(asset)
    }

    static class NativeSerializer extends StdSerializer<Request> {

        NativeSerializer() {
            super(Request)
        }

        @Override
        void serialize(Request request, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                throws IOException {
            def string = Dependencies.objectMapperWrapper.encode(request)
            jsonGenerator.writeString(string)
        }
    }

    static class NativeDeserializer extends StdDeserializer<Request> {

        protected NativeDeserializer() {
            super(Request)
        }

        @Override
        Request deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            jsonParser.text ? Dependencies.objectMapperWrapper.decode(jsonParser.text, Request) : null
        }
    }
}
