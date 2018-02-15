package org.rtb.vexing.model.openrtb.ext.request.appnexus;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.adapter.appnexus.model.AppnexusKeyVal;

import java.math.BigDecimal;
import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtImpAppnexus {

    @JsonProperty("placementId")
    Integer placementId;

    @JsonProperty("invCode")
    String invCode;

    String member;

    List<AppnexusKeyVal> keywords;

    @JsonProperty("trafficSourceCode")
    String trafficSourceCode;

    BigDecimal reserve;

    String position;
}
