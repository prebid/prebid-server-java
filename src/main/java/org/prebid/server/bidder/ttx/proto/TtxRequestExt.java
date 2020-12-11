package org.prebid.server.bidder.ttx.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class TtxRequestExt {

    List<TtxCaller> caller;
}
