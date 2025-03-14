package org.prebid.server.proto.openrtb.ext.request;

import java.util.Set;

public interface AlternateBidder {

    Boolean getEnabled();

    Set<String> getAllowedBidderCodes();
}
