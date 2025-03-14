package org.prebid.server.bidder.resetdigital.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
@Builder
public class ResetDigitalImpMediaType {

    List<List<Integer>> sizes;

    List<String> mimes;
}
