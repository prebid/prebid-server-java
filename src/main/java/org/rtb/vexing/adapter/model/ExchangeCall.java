package org.rtb.vexing.adapter.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.model.response.BidderDebug;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExchangeCall {

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
