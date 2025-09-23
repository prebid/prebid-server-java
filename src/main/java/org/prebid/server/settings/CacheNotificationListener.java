package org.prebid.server.settings;

import java.util.List;
import java.util.Map;

public interface CacheNotificationListener<T> {

    void save(Map<String, T> requests, Map<String, T> imps);

    void invalidate(List<String> requests, List<String> imps);
}
