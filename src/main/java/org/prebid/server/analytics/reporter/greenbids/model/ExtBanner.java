package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ExtBanner {

    List<List<Integer>> sizes;

    Integer pos;

    String name;
}
