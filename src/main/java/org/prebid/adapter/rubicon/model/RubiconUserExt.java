package org.prebid.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.model.openrtb.ext.request.ExtUserDigiTrust;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconUserExt {

    RubiconUserExtRp rp;

    ExtUserDigiTrust digitrust;
}
