package org.prebid.server.it.hooks;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.util.StreamUtil;

import java.util.Objects;

public interface SampleItModuleUtil {

    String MODULE_EXT = "sample-it-module";

    static boolean shouldHookUpdateBidRequest(BidRequest bidRequest, String hookCode) {
        final ExtRequest ext = bidRequest.getExt();
        final JsonNode extModule = ext != null ? ext.getProperty(MODULE_EXT) : null;
        final JsonNode update = extModule != null ? extModule.get("update") : null;

        return update != null && StreamUtil.asStream(update.spliterator())
                .anyMatch(element -> Objects.equals(element.asText(), hookCode));
    }
}
