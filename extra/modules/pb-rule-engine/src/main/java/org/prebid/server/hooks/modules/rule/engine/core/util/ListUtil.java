package org.prebid.server.hooks.modules.rule.engine.core.util;

import java.util.Collection;
import java.util.Objects;

public class ListUtil {

    private ListUtil() {
    }

    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty() && collection.stream().anyMatch(Objects::nonNull);
    }
}
