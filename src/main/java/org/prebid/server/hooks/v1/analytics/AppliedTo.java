package org.prebid.server.hooks.v1.analytics;

import java.util.List;

public interface AppliedTo {

    List<String> imp();

    List<String> bidders();

    boolean request();

    boolean response();

    List<String> bidId();
}
