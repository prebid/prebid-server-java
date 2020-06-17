package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.json.FpdSiteDeserializer;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfigFpd {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.site
     */
    @JsonDeserialize(using = FpdSiteDeserializer.class)
    Site site;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.app
     */
    App app;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.user
     */
    User user;
}
