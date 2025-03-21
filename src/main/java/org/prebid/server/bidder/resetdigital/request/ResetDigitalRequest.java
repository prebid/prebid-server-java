package org.prebid.server.bidder.resetdigital.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ResetDigitalRequest {

    ResetDigitalSite site;

    List<ResetDigitalImp> imps;
}
