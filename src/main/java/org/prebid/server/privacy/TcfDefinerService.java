package org.prebid.server.privacy;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.model.TcfResponse;

import java.util.Set;

public class TcfDefinerService {

    public Future<TcfResponse> resultFor(Set<String> bidderNames,
                                         Set<Integer> vendorIds,
                                         String gdpr,
                                         String gdprConsent,
                                         String ipAddress,
                                         Timeout timeout) {

        throw new UnsupportedOperationException();
    }
}
