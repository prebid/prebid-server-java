package org.prebid.server.bidder.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconUserExt {

    RubiconUserExtRp rp;

    ExtUserDigiTrust digitrust;
}
