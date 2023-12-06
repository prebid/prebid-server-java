package org.prebid.server.bidder.teads;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.teads.TeadsImpExt;

@Value(staticConstructor = "of")
public class TeadsImpExtKV {

    TeadsImpExt kv;

}
