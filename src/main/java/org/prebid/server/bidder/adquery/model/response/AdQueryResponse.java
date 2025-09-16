package org.prebid.server.bidder.adquery.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class AdQueryResponse {

    AdQueryDataResponse data;
}
