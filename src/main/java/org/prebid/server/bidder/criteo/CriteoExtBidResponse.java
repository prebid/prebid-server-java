package org.prebid.server.bidder.criteo;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;

import java.util.List;

@Value(staticConstructor = "of")
public class CriteoExtBidResponse {

    List<ExtIgi> igi;
}
