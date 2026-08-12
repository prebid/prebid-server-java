package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.util.Set;

@Value(staticConstructor = "of")
public class MagniteImpExtRpRtb {

    Set<ImpMediaType> formats;
}
