package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MagniteTargetingExtRp {

    List<MagniteTargeting> targeting;
}
