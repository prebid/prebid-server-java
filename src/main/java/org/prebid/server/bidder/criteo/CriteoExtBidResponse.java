package org.prebid.server.bidder.criteo;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class CriteoExtBidResponse {

    List<CriteoIgiExtBidResponse> igi;
}
