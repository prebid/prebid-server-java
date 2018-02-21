package org.rtb.vexing.adapter.rubicon.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.model.openrtb.ext.request.ExtUserDigiTrust;

@AllArgsConstructor(staticName = "of")
@Value
public final class RubiconUserExt {

    RubiconUserExtRp rp;

    ExtUserDigiTrust digitrust;
}
