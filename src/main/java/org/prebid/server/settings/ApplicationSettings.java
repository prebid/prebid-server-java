package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredProfileResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Map;
import java.util.Set;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId, Timeout timeout);

    Future<StoredDataResult> getStoredData(String accountId,
                                           Set<String> requestIds,
                                           Set<String> impIds,
                                           Timeout timeout);

    Future<StoredDataResult> getAmpStoredData(String accountId,
                                              Set<String> requestIds,
                                              Set<String> impIds,
                                              Timeout timeout);

    Future<StoredDataResult> getVideoStoredData(String accountId,
                                                Set<String> requestIds,
                                                Set<String> impIds,
                                                Timeout timeout);

    Future<StoredProfileResult> getProfiles(String accountId,
                                            Set<String> requestIds,
                                            Set<String> impIds,
                                            Timeout timeout);

    Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout);

    Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout);
}
