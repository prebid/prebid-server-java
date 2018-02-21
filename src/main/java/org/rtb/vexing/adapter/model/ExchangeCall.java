package org.rtb.vexing.adapter.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.model.response.BidderDebug;

@AllArgsConstructor
@Value
public final class ExchangeCall {

    BidRequest bidRequest;

    BidResponse bidResponse;

    BidderDebug bidderDebug;

    String error;

    boolean timedOut;

    public static ExchangeCall error(BidderDebug bidderDebug, String error) {
        return new ExchangeCall(null, null, bidderDebug, error, false);
    }

    public static ExchangeCall timeout(BidderDebug bidderDebug, String error) {
        return new ExchangeCall(null, null, bidderDebug, error, true);
    }

    public static ExchangeCall success(BidRequest bidRequest, BidResponse bidResponse, BidderDebug bidderDebug) {
        return new ExchangeCall(bidRequest, bidResponse, bidderDebug, null, false);
    }

    public static ExchangeCall empty(BidderDebug bidderDebug) {
        return new ExchangeCall(null, null, bidderDebug, null, false);
    }
}
