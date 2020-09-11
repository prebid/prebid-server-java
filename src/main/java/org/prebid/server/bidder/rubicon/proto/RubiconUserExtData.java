package org.prebid.server.bidder.rubicon.proto;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class RubiconUserExtData {

    List<RubiconUserExtDataPpuid> ppuids;
}
