package org.prebid.server.settings.helper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParametrizedQueryPostgresHelper implements ParametrizedQueryHelper {

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("(%s)|(%s)".formatted(REQUEST_ID_PLACEHOLDER, IMP_ID_PLACEHOLDER));

    @Override
    public String replaceAccountIdPlaceholder(String query) {
        return query.replace(ACCOUNT_ID_PLACEHOLDER, "$1");
    }

    @Override
    public String replaceRequestAndImpIdPlaceholders(String query, int requestIdNumber, int impIdNumber) {
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(query);

        int i = 0;
        final StringBuilder queryBuilder = new StringBuilder();
        while (matcher.find()) {
            final int paramsNumber = matcher.group(1) != null ? requestIdNumber : impIdNumber;
            matcher.appendReplacement(queryBuilder, parameterHolders(paramsNumber, i));
            i += paramsNumber;
        }
        matcher.appendTail(queryBuilder);

        return queryBuilder.toString();
    }

    @Override
    public String replaceStoredResponseIdPlaceholders(String query, int idsNumber) {
        return query.replaceAll(RESPONSE_ID_PLACEHOLDER, parameterHolders(idsNumber, 0));
    }

    private static String parameterHolders(int paramsSize, int start) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(start, start + paramsSize)
                .mapToObj(i -> "\\$" + (i + 1))
                .collect(Collectors.joining(","));
    }
}
