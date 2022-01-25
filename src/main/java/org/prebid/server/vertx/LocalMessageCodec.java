package org.prebid.server.vertx;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageCodec;

/**
 * Message codec intended for use with objects passed around via {@link EventBus} only locally, i.e. within one JVM.
 */
public class LocalMessageCodec implements MessageCodec<Object, Object> {

    private static final String CODEC_NAME = "LocalMessageCodec";

    public static MessageCodec<Object, Object> create() {
        return new LocalMessageCodec();
    }

    @Override
    public void encodeToWire(Buffer buffer, Object source) {
        throw new UnsupportedOperationException("Serialization is not supported by this message codec");
    }

    @Override
    public Object decodeFromWire(int pos, Buffer buffer) {
        throw new UnsupportedOperationException("Deserialization is not supported by this message codec");
    }

    @Override
    public Object transform(Object source) {
        return source;
    }

    @Override
    public String name() {
        return codecName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }

    public static String codecName() {
        return CODEC_NAME;
    }
}
