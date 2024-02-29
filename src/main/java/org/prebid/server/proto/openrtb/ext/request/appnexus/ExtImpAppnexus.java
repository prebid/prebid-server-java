package org.prebid.server.proto.openrtb.ext.request.appnexus;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class ExtImpAppnexus {

    @JsonAlias("placementId")
    Integer placementId;

    @JsonAlias("invCode")
    String invCode;

    String member;

    JsonNode keywords;

    @JsonAlias("trafficSourceCode")
    String trafficSourceCode;

    BigDecimal reserve;

    String position;

    @JsonProperty("use_pmt_rule")
    @JsonAlias("use_payment_rule")
    Boolean usePaymentRule;

    JsonNode privateSizes;

    boolean generateAdPodId;

    String extInvCode;

    String externalImpId;
}
