package org.prebid.server.deals.events;

import org.prebid.server.deals.model.AdminCentralResponse;

public interface AdminEventProcessor {

    void processAdminCentralEvent(AdminCentralResponse adminCentralResponse);
}
