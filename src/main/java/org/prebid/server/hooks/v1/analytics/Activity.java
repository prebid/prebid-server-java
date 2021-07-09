package org.prebid.server.hooks.v1.analytics;

import java.util.List;

public interface Activity {

    String name();

    String status();

    List<Result> results();
}
