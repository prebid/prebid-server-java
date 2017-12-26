package org.rtb.vexing.adapter.appnexus.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class AppnexusParams {

    Integer placementId;

    String invCode;

    String member;

    List<AppnexusKeyVal> keywords;

    String trafficSourceCode;

    BigDecimal reserve;

    String position;
}
