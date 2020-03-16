package org.prebid.server.privacy;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.model.TcfResponse;

import java.util.Collection;

public class TcfDefinerService {

    public Future<TcfResponse> resultByVendor(Collection<String> bidderNames,
                                              String gdpr,
                                              String gdprConsent,
                                              String ipAddress,
                                              Timeout timeout) {

        throw new UnsupportedOperationException();
    }
}