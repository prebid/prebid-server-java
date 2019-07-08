package org.prebid.server.bidder.rubicon.proto;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.ExtUserEid;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;

import java.util.List;

@Builder
@Value
public class RubiconUserExt {

    String consent;

    ExtUserDigiTrust digitrust;

    List<ExtUserEid> eids;

    List<ExtUserTpIdRubicon> tpid;

    RubiconUserExtRp rp;
}
