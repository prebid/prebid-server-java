package org.prebid.server.hooks.modules.id5.userid.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;

import java.util.Optional;

public class BidRequestUtils {

    private BidRequestUtils() { }

    public static final String ID5_ID_SOURCE = "id5-sync.com";

    public static boolean isId5IdPresent(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getUser())
                .map(User::getEids)
                .map(eids -> eids.stream().anyMatch(eid -> ID5_ID_SOURCE.equals(eid.getSource())))
                .orElse(false);
    }

}
