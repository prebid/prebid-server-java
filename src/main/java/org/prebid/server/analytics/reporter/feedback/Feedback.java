package org.prebid.server.analytics.reporter.feedback;

import com.iab.openrtb.request.Imp;

import java.math.BigDecimal;

public class Feedback {
    public Feedback(String _impressionId, String _bidder, BigDecimal _revenue) {
        this._impressionId = _impressionId;
        this._bidder = _bidder;
        this._revenue = _revenue;
    }

    private int _siteId;

    private String _country;

    private String _platform;

    private String _networkAdUnit;

    private String _impressionId;

    private String _bidder;

    private BigDecimal _revenue;

    public void sendFeedback()
    {
        //send feedback here
    }


    public String getImpId() {
        return this._impressionId;
    }
}
