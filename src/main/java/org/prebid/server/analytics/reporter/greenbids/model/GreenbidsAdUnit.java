package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class GreenbidsAdUnit {

    String code;

    GreenbidsUnifiedCode unifiedCode;

    MediaTypes mediaTypes;

    List<GreenbidsBids> bids;
}



