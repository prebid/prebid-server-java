package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.JacksonMapper;

/**
 * Handles HTTP request for pbs project version.
 */
public class VersionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VersionHandler.class);

    private final String revisionResponseBody;

    public VersionHandler(String version, String commitHash, JacksonMapper mapper) {
        this.revisionResponseBody = createRevisionResponseBody(version, commitHash, mapper);
    }

    private static String createRevisionResponseBody(String version, String commitHash, JacksonMapper mapper) {
        try {
            return mapper.mapper().writeValueAsString(RevisionResponse.of(commitHash, version));
        } catch (JsonProcessingException e) {
            logger.error("/version Critical error when trying to marshal revision response: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Responds with commit revision.
     */
    @Override
    public void handle(RoutingContext context) {
        if (StringUtils.isNotBlank(revisionResponseBody)) {
            context.response().end(revisionResponseBody);
        } else {
            context.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class RevisionResponse {

        String revision;

        String version;
    }
}
