package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;

/**
 * Handles HTTP request for pbs project version.
 */
public class VersionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(VersionHandler.class);

    private static final String NOT_SET = "not-set";

    private final String revisionResponseBody;

    private VersionHandler(String revisionResponseBody) {
        this.revisionResponseBody = revisionResponseBody;
    }

    public static VersionHandler create(String revisionFilePath, JacksonMapper mapper) {
        Revision revision;
        try {
            revision = mapper.mapper().readValue(ResourceUtil.readFromClasspath(revisionFilePath), Revision.class);
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Was not able to read revision file {0}. Reason: {1}", revisionFilePath, e.getMessage());
            revision = Revision.of(NOT_SET, NOT_SET);
        }
        return new VersionHandler(createRevisionResponseBody(revision, mapper));
    }

    private static String createRevisionResponseBody(Revision revision, JacksonMapper mapper) {
        try {
            return mapper.mapper().writeValueAsString(RevisionResponse.of(
                    revision.commitHash != null ? revision.commitHash : NOT_SET,
                    revision.pbsVersion != null ? revision.pbsVersion : NOT_SET));
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
    private static class Revision {

        @JsonProperty("git.commit.id")
        String commitHash;

        @JsonProperty("git.build.version")
        String pbsVersion;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class RevisionResponse {

        String revision;

        String version;
    }
}
