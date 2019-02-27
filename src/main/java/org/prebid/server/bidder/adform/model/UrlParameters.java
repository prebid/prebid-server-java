package org.prebid.server.bidder.adform.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class UrlParameters {

    List<Long> masterTagIds;

    List<String> priceTypes;

    String endpointUrl;

    String tid;

    String ip;

    String advertisingId;

    boolean secure;

    String gdprApplies;

    String consent;

    String cur;
}
