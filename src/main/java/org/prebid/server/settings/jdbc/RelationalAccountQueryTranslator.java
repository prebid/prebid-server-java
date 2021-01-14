package org.prebid.server.settings.jdbc;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.jdbc.model.SqlQuery;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.AccountGdprConfig;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

public class RelationalAccountQueryTranslator implements AccountQueryTranslator {

    private static final String ACCOUNT_ID_PLACEHOLDER = "%ACCOUNT_ID%";
    private static final String QUERY_PARAM_PLACEHOLDER = "?";

    /**
     * Query to select account by ids.
     */
    private final String selectAccountQuery;

    private final JacksonMapper mapper;

    public RelationalAccountQueryTranslator(String selectAccountQuery,
                                            JacksonMapper mapper) {

        this.selectAccountQuery = Objects.requireNonNull(selectAccountQuery)
                .replace(ACCOUNT_ID_PLACEHOLDER, QUERY_PARAM_PLACEHOLDER);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public SqlQuery selectQuery(String accountId) {
        return SqlQuery.of(selectAccountQuery, Collections.singletonList(accountId));
    }

    @Override
    public Account translateQueryResult(ResultSet result) {
        return mapToModelOrError(result, row -> Account.builder()
                .id(row.getString(0))
                .priceGranularity(row.getString(1))
                .bannerCacheTtl(row.getInteger(2))
                .videoCacheTtl(row.getInteger(3))
                .eventsEnabled(row.getBoolean(4))
                .enforceCcpa(row.getBoolean(5))
                .gdpr(toModel(row.getString(6), AccountGdprConfig.class))
                .analyticsSamplingFactor(row.getInteger(7))
                .truncateTargetAttr(row.getInteger(8))
                .defaultIntegration(row.getString(9))
                .analyticsConfig(toModel(row.getString(10), AccountAnalyticsConfig.class))
                .bidValidations(toModel(row.getString(11), AccountBidValidationConfig.class))
                .build());
    }

    /**
     * Transforms the first row of {@link ResultSet} to required object or returns null.
     * <p>
     * Note: mapper should never throw exception in case of using
     * {@link org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient}.
     */
    private static <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
        return result != null && CollectionUtils.isNotEmpty(result.getResults())
                ? mapper.apply(result.getResults().get(0))
                : null;
    }

    private <T> T toModel(String source, Class<T> targetClass) {
        try {
            return source != null ? mapper.decodeValue(source, targetClass) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
