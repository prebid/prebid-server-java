package org.prebid.server.bidder.rubicon.proto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;

@AllArgsConstructor(staticName = "of")
@Value
public class RubiconUserExt {

    RubiconUserExtRp rp;

    ExtUserDigiTrust digitrust;

    String consent;
}
