package org.prebid.server.auction.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.execution.Timeout;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.settings.model.Account;

@Value(staticConstructor = "of")
public class PreAuctionContext {

    Account account;

    Timeout timeout;

    BidRequest bidRequest;

    HttpRequestWrapper httpRequest;

    public PreAuctionContext with(BidRequest bidRequest) {
        return of(this.account, this.timeout, bidRequest, this.httpRequest);
    }
}
