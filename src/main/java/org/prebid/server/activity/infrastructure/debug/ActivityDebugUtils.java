package org.prebid.server.activity.infrastructure.debug;

import java.util.List;

public class ActivityDebugUtils {

    public static Object asLogEntry(Object object) {
        return object instanceof Loggable loggable
                ? loggable.asLogEntry()
                : object.toString();
    }

    public static Object asLogEntry(List<?> objects) {
        return objects.stream()
                .map(ActivityDebugUtils::asLogEntry)
                .toList();
    }
}
