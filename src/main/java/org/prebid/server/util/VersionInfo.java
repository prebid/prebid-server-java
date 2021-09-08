package org.prebid.server.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.json.JacksonMapper;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value
public class VersionInfo {

    private static final Logger logger = LoggerFactory.getLogger(VersionInfo.class);
    private static final String UNDEFINED = "undefined";

    String version;
    String commitHash;

    private VersionInfo(String version, String commitHash) {
        this.version = version;
        this.commitHash = commitHash;
    }

    public static VersionInfo create(String revisionFilePath, JacksonMapper jacksonMapper) {
        Revision revision;
        try {
            revision = jacksonMapper.mapper().readValue(ResourceUtil.readFromClasspath(revisionFilePath),
                    Revision.class);
        } catch (IllegalArgumentException | IOException e) {
            logger.error("Was not able to read revision file {0}. Reason: {1}", revisionFilePath, e.getMessage());
            return new VersionInfo(UNDEFINED, UNDEFINED);
        }
        final String pbsVersion = revision.getPbsVersion();
        final String commitHash = revision.getCommitHash();
        return new VersionInfo(
                pbsVersion != null ? extractVersion(pbsVersion) : UNDEFINED,
                commitHash != null ? commitHash : UNDEFINED);
    }

    private static String extractVersion(String buildVersion) {
        final Pattern versionPattern = Pattern.compile("\\d+\\.\\d+\\.\\d");
        final Matcher versionMatcher = versionPattern.matcher(buildVersion);

        return versionMatcher.lookingAt() ? versionMatcher.group() : null;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Revision {

        @JsonProperty("git.commit.id")
        String commitHash;

        @JsonProperty("git.build.version")
        String pbsVersion;
    }
}
