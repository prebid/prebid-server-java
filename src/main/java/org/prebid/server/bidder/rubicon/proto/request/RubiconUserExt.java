package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.rubicon.ExtUserTpIdRubicon;

import java.util.List;

@Builder
@Value
public class RubiconUserExt {

    RubiconUserExtRp rp;

    List<ExtUserTpIdRubicon> tpid;

    String liverampIdl;
}
