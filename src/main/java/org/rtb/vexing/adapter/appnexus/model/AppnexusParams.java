package org.rtb.vexing.adapter.appnexus.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public final class AppnexusParams {

    Integer placementId;

    String invCode;

    String member;

    List<AppnexusKeyVal> keywords;

    String trafficSourceCode;

    BigDecimal reserve;

    String position;
}
