package org.prebid.server.auction.mediatypeprocessor;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.settings.model.Account;

public interface MediaTypeProcessor {

    MediaTypeProcessingResult process(BidRequest bidRequest, String bidderName, BidderAliases aliases, Account account);
}
