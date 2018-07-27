package org.prebid.server.bidder.adform.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdformDigitrustPrivacy {

    Boolean optout;
}
