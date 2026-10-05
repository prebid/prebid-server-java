package org.prebid.server.proto.openrtb.ext.request.msft;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExtImpMsft {

    Integer placementId;

    Integer member;

    String invCode;

    Boolean allowSmallerSizes;

    Boolean usePmtRule;

    String keywords;

    String trafficSourceCode;

    String pubclick;

    String extInvCode;

    String extImpId;

    List<Integer> bannerFrameworks;
}
