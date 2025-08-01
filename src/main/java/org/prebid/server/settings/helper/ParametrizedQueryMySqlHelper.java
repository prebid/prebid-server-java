package org.prebid.server.settings.helper;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParametrizedQueryMySqlHelper implements ParametrizedQueryHelper {

    private static final String PARAMETER_PLACEHOLDER = "?";

    @Override
    public String replaceAccountIdPlaceholder(String query) {
        return query.replace(ACCOUNT_ID_PLACEHOLDER, PARAMETER_PLACEHOLDER);
    }

    @Override
    public String replaceRequestAndImpIdPlaceholders(String query, int requestIdNumber, int impIdNumber) {
        return query
                .replace(REQUEST_ID_PLACEHOLDER, parameterHolders(requestIdNumber))
                .replace(IMP_ID_PLACEHOLDER, parameterHolders(impIdNumber));
    }

    @Override
    public String replaceStoredResponseIdPlaceholders(String query, int idsNumber) {
        return query.replace(RESPONSE_ID_PLACEHOLDER, parameterHolders(idsNumber));
    }

    private static String parameterHolders(int paramsSize) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(0, paramsSize)
                .mapToObj(i -> PARAMETER_PLACEHOLDER)
                .collect(Collectors.joining(","));
    }
}
