package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.FetchStatus
import org.prebid.server.functional.model.request.auction.Imp

import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@EqualsAndHashCode
class AnalyticResult {

    String name
    FetchStatus status
    List<ImpResult> results

    static AnalyticResult buildFromImp(Imp imp) {
        def appliedTo = new AppliedTo(impIds: [imp.id], bidder: imp.ext.prebid.bidder.configuredBidders.first())
        def impResult = new ImpResult(status: 'success-block', values: new ModuleValue(richmediaFormat: 'mraid'), appliedTo: appliedTo)
        new AnalyticResult(name: 'reject-richmedia', status: SUCCESS, results: [impResult])
    }
}
