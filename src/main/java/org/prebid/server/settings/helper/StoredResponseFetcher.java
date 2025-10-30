package org.prebid.server.settings.helper;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Set;

@FunctionalInterface
public interface StoredResponseFetcher {

    Future<StoredResponseDataResult> apply(Set<String> responseIds, Timeout timeout);
}
