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

    private static final int MAX_BODY_LENGTH = 1024 * 1024;

    private final ThreadLocal<byte[]> intermediateBuffer = ThreadLocal.withInitial(() -> new byte[1024]);
    private final ThreadLocal<byte[]> inputBuffer = ThreadLocal.withInitial(() -> new byte[MAX_BODY_LENGTH]);
    private final ThreadLocal<byte[]> outputBuffer = ThreadLocal.withInitial(() -> new byte[2 * MAX_BODY_LENGTH]);

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
            respondWithBadRequest(routingContext, "Invalid gzip body: " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            respondWithBadRequest(routingContext, "Too big body: " + e.getMessage());
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
            return compressed;
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
