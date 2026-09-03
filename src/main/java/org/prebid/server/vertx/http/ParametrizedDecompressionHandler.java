package org.prebid.server.vertx.http;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RoutingContextInternal;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class ParametrizedDecompressionHandler implements Handler<RoutingContext> {

    private final ThreadLocal<byte[]> intermediateBuffer;
    private final ThreadLocal<byte[]> inputBuffer;
    private final ThreadLocal<byte[]> outputBuffer;

    public ParametrizedDecompressionHandler(int maxBodySize) {
        intermediateBuffer = ThreadLocal.withInitial(() -> new byte[16384]);
        inputBuffer = ThreadLocal.withInitial(() -> new byte[maxBodySize]);
        outputBuffer = ThreadLocal.withInitial(() -> new byte[2 * maxBodySize]);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (!StringUtils.equalsAny(routingContext.request().getParam("gzip"), "1", "true")) {
            routingContext.next();
            return;
        }

        try {
            final Buffer decompressed = decompressGzip(routingContext.body().buffer());
            ((RoutingContextInternal) routingContext).setBody(decompressed);
            routingContext.next();
        } catch (IOException e) {
            respondWithBadRequest(routingContext, "Invalid body: " + e.getMessage());
        }
    }

    private static void respondWithBadRequest(RoutingContext routingContext, String message) {
        routingContext.response()
                .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                .end(message);
    }

    private Buffer decompressGzip(Buffer compressed) throws IOException {
        final byte[] decompressionBuffer = intermediateBuffer.get();
        final byte[] compressedBuffer = inputBuffer.get();
        final byte[] decompressedBuffer = outputBuffer.get();

        compressed.getBytes(compressedBuffer);
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressedBuffer);
                GZIPInputStream gzip = new GZIPInputStream(input);
                FastByteArrayOutputStream baos = new FastByteArrayOutputStream(decompressedBuffer)) {

            int totalLen = 0;
            int len;
            while ((len = gzip.read(decompressionBuffer)) > 0) {
                baos.write(decompressionBuffer, 0, len);
                totalLen += len;
            }

            compressed.setBytes(0, baos.getBuffer(), 0, totalLen);
            return compressed.slice(0, totalLen);
        }
    }

    private static class FastByteArrayOutputStream extends ByteArrayOutputStream {

        FastByteArrayOutputStream(byte[] buf) {
            this.buf = buf;
        }

        public byte[] getBuffer() {
            return buf;
        }
    }
}
