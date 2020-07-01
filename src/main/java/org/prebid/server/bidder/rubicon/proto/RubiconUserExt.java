package org.prebid.server.bidder.rubicon.proto;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;

import java.util.List;

@Builder
@Value
public class RubiconUserExt {

    List<ExtUserTpIdRubicon> tpid;

    RubiconUserExtRp rp;
}
