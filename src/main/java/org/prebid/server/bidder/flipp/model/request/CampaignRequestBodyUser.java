package org.prebid.server.bidder.flipp.model.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class CampaignRequestBodyUser {

    String key;
}
