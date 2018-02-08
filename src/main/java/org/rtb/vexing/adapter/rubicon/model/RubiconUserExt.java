package org.rtb.vexing.adapter.rubicon.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.model.openrtb.ext.request.ExtUserDigiTrust;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public final class RubiconUserExt {

    RubiconUserExtRp rp;

    ExtUserDigiTrust digitrust;
}
