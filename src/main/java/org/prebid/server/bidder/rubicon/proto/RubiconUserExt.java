package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserTpId;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconUserExt {

    RubiconUserExtRp rp;

    String consent;

    ExtUserDigiTrust digitrust;

    List<ExtUserTpId> tpid;
}
