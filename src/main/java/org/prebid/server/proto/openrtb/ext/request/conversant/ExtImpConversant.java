package org.prebid.server.proto.openrtb.ext.request.conversant;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public class ExtImpConversant {

    String siteId;

    Integer secure;

    String tagId;

    Integer position;

    BigDecimal bidfloor;

    Integer mobile;

    List<String> mimes;

    List<Integer> api;

    List<Integer> protocols;

    Integer maxduration;
}
