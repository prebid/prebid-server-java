package org.prebid.server.bidder.resetdigital.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ResetDigitalRequest {

    ResetDigitalSite site;

    List<ResetDigitalImp> imps;
}
