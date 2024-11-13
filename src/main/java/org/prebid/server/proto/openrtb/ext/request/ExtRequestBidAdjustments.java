package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class ExtRequestBidAdjustments {

    Map<String, Map<String, Map<String, List<ExtRequestBidAdjustmentsRule>>>> mediatype;

}
