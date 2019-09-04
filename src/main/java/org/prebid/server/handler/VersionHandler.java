package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;

/**
 * Handles HTTP request for pbs project version.
 */
public class VersionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VersionHandler.class);

    private static final String DEFAULT_REVISION_VALUE = "not-set";

    private Revision revision;

    private VersionHandler(Revision revision) {
        this.revision = revision;
    }

    public static VersionHandler create(String revisionFilePath) {
        Revision revision;
        try {
            revision = Json.mapper.readValue(ResourceUtil.readFromClasspath(revisionFilePath), Revision.class);
        } catch (IllegalArgumentException | IOException e) {
            logger.warn("Was not able to read revision file {0}. Reason: {1}", revisionFilePath, e.getMessage());
            revision = Revision.of(DEFAULT_REVISION_VALUE);
        }
        return new VersionHandler(revision.commitHash == null ? Revision.of(DEFAULT_REVISION_VALUE) : revision);
    }

    /**
     * Responds with commit revision.
     */
    @Override
    public void handle(RoutingContext context) {
        final RevisionResponse revisionResponse = RevisionResponse.of(revision.commitHash);
        final String revisionResponseJson;
        try {
            revisionResponseJson = Json.mapper.writeValueAsString(revisionResponse);
        } catch (JsonProcessingException e) {
            logger.error("/version Critical error when trying to marshal revision response: %s", e.getMessage());
            context.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
            return;
        }
        context.response().end(revisionResponseJson);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Revision {

        @JsonProperty("git.commit.id")
        String commitHash;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class RevisionResponse {

        String revision;
    }
}
