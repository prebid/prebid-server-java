package org.prebid.server.proto.openrtb.ext.request;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfigFpd {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.site
     */
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
