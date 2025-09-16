package org.prebid.server.settings;

import java.util.List;
import java.util.Map;

public interface CacheNotificationListener {

    void save(Map<String, String> requests, Map<String, String> imps);

    void invalidate(List<String> requests, List<String> imps);
}
