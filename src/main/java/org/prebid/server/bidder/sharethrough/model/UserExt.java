package org.prebid.server.bidder.sharethrough.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class UserExt {

    String consent;

    List<ExtUserEid> eids;
}

