package org.prebid.server.util;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

public class PbsUtil {

    private PbsUtil() {
    }

    public static ExtRequestPrebid extRequestPrebid(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        return requestExt != null ? requestExt.getPrebid() : null;
    }
}
