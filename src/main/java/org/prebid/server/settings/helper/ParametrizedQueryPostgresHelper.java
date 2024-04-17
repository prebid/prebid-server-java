package org.prebid.server.settings.helper;

import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParametrizedQueryPostgresHelper implements ParametrizedQueryHelper {

    @Override
    public String replaceAccountIdPlaceholder(String query) {
        return query.replace(ACCOUNT_ID_PLACEHOLDER, "$1");
    }

    @Override
    public String replaceStoredResponseIdPlaceholders(String query, int idsNumber) {
        return query.replaceAll(RESPONSE_ID_PLACEHOLDER, parameterHolders(idsNumber, 0));
    }

    @Override
    public String replaceRequestAndImpIdPlaceholders(String query, int requestIdNumber, int impIdNumber) {
        final int requestIdCount = StringUtils.countMatches(query, REQUEST_ID_PLACEHOLDER);
        final int impIdCount = StringUtils.countMatches(query, IMP_ID_PLACEHOLDER);

        int start = 0;
        String resultQuery = query;
        for (int i = 0; i < requestIdCount; i++) {
            resultQuery = resultQuery.replaceFirst(REQUEST_ID_PLACEHOLDER, parameterHolders(requestIdNumber, start));
            start += requestIdNumber;
        }

        for (int i = 0; i < impIdCount; i++) {
            resultQuery = resultQuery.replaceFirst(IMP_ID_PLACEHOLDER, parameterHolders(impIdNumber, start));
            start += impIdNumber;
        }

        return resultQuery;
    }

    private static String parameterHolders(int paramsSize, int start) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(start, start + paramsSize)
                .mapToObj(i -> "\\$" + (i + 1))
                .collect(Collectors.joining(","));
    }
}
