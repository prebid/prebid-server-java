package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.util.Set;

@Value(staticConstructor = "of")
public class RubiconImpExtRpRtb {

    Set<ImpMediaType> formats;
}
