package org.prebid.server.deals.proto.report;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

@Value
@AllArgsConstructor(staticName = "of")
public class DeliveryProgressReportBatch {

    Set<DeliveryProgressReport> reports;

    String reportId;

    String dataWindowEndTimeStamp;

    public void removeReports(Set<DeliveryProgressReport> reports) {
        this.reports.removeAll(reports);
    }
}
