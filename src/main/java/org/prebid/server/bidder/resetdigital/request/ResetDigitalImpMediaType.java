package org.prebid.server.bidder.resetdigital.request;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ResetDigitalImpMediaType {

    List<List<Integer>> sizes;

    List<String> mimes;
}
