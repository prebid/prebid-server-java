package org.prebid.server.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MultiMap {

    String get(CharSequence name);

    String get(String key);

    List<String> getAll(CharSequence name);

    List<String> getAll(String name);

    List<Map.Entry<String, String>> entries();

    boolean contains(CharSequence name);

    boolean contains(String name);

    Set<String> names();

    boolean isEmpty();
}
