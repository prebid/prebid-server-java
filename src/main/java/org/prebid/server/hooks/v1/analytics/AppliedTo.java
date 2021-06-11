package org.prebid.server.hooks.v1.analytics;

import java.util.List;

public interface AppliedTo {

    List<String> impIds();

    List<String> bidders();

    boolean request();

    boolean response();

    List<String> bidIds();
}
