package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class BidRequestContext {
    BidRequest bidRequest;

    Map<String, List<String>> bidderErrors;

    Map<String, String> aliases;

    ExtBidRequest extBidRequest;
}
