package org.prebid.server.settings;

import java.util.List;
import java.util.Map;

<<<<<<< HEAD
public interface CacheNotificationListener<T> {

    void save(Map<String, T> requests, Map<String, T> imps);
=======
public interface CacheNotificationListener {

    void save(Map<String, String> requests, Map<String, String> imps);
>>>>>>> 04d9d4a13 (Initial commit)

    void invalidate(List<String> requests, List<String> imps);
}
