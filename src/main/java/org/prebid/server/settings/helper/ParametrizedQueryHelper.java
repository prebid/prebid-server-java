package org.prebid.server.settings.helper;

public interface ParametrizedQueryHelper {

    String ACCOUNT_ID_PLACEHOLDER = "%ACCOUNT_ID%";
    String REQUEST_ID_PLACEHOLDER = "%REQUEST_ID_LIST%";
    String IMP_ID_PLACEHOLDER = "%IMP_ID_LIST%";
    String RESPONSE_ID_PLACEHOLDER = "%RESPONSE_ID_LIST%";

    String replaceAccountIdPlaceholder(String query);

    String replaceStoredResponseIdPlaceholders(String query, int idsNumber);

    String replaceRequestAndImpIdPlaceholders(String query, int requestIdNumber, int impIdNumber);

}
