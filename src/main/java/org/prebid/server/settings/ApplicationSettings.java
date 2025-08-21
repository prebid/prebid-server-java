package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Map;
import java.util.Set;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId, Timeout timeout);

    Future<StoredDataResult<String>> getStoredData(String accountId,
                                                   Set<String> requestIds,
                                                   Set<String> impIds,
                                                   Timeout timeout);

    Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                      Set<String> requestIds,
                                                      Set<String> impIds,
                                                      Timeout timeout);

    Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                        Set<String> requestIds,
                                                        Set<String> impIds,
                                                        Timeout timeout);

    Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout);

    Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout);

    Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout);
}
