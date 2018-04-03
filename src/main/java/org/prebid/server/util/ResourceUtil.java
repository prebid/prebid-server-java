package org.prebid.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * This class consists of {@code static} utility methods for operating application resources.
 */
public class ResourceUtil {

    private ResourceUtil() {
    }

    /**
     * Reads files from classpath. Throws {@link IllegalArgumentException} if file was not found.
     */
    public static String readFromClasspath(String path) throws IOException {
        final InputStream resourceAsStream = ResourceUtil.class.getClassLoader().getResourceAsStream(path);

        if (resourceAsStream == null) {
            throw new IllegalArgumentException(String.format("Could not find file at path: %s", path));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream,
                StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
