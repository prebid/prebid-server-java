package org.prebid.server.settings.helper;

import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;

public class ParametrizedQueryPostgresHelperTest extends VertxTest {

    private final ParametrizedQueryPostgresHelper target = new ParametrizedQueryPostgresHelper();

    @Test
    public void replaceAccountIdPlaceholderShouldReplacePlaceholderWithWildCard() {
        // when
        final String result = target.replaceAccountIdPlaceholder("SELECT * FROM table WHERE id = %ACCOUNT_ID%");

        // then
        assertThat(result).isEqualTo("SELECT * FROM table WHERE id = $1");
    }

    @Test
    public void replaceStoredResponseIdPlaceholdersShouldReplacePlaceholderWithWildCards() {
        // when
        final String result = target.replaceStoredResponseIdPlaceholders(
                "SELECT responseId, responseData FROM stored_responses WHERE responseId IN (%RESPONSE_ID_LIST%)",
                3);

        // then
        assertThat(result)
                .isEqualTo("SELECT responseId, responseData FROM stored_responses WHERE responseId IN ($1,$2,$3)");
    }

    @Test
    public void replaceStoredResponseIdPlaceholdersShouldReplacePlaceholderWithNullWhenParamsNumberAreZero() {
        // when
        final String result = target.replaceStoredResponseIdPlaceholders(
                "SELECT responseId, responseData FROM stored_responses WHERE responseId IN (%RESPONSE_ID_LIST%)",
                0);

        // then
        assertThat(result)
                .isEqualTo("SELECT responseId, responseData FROM stored_responses WHERE responseId IN (NULL)");
    }

    @Test
    public void replaceRequestAndImpIdPlaceholdersShouldReplacePlaceholderWithWildCards() {
        // when
        final String result = target.replaceRequestAndImpIdPlaceholders(
                "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                        + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests2 "
                        + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                        + "WHERE impid IN (%IMP_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps2 "
                        + "WHERE impid IN (%IMP_ID_LIST%)",
                2, 3);

        // then
        assertThat(result).isEqualTo("SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                + "WHERE reqid IN ($1,$2) "
                + "UNION ALL "
                + "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests2 "
                + "WHERE reqid IN ($3,$4) "
                + "UNION ALL "
                + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                + "WHERE impid IN ($5,$6,$7) "
                + "UNION ALL "
                + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps2 "
                + "WHERE impid IN ($8,$9,$10)");
    }

    @Test
    public void replaceRequestAndImpIdPlaceholdersShouldReplacePlaceholderWithNullsWhenIdsNumberIsZero() {
        // when
        final String result = target.replaceRequestAndImpIdPlaceholders(
                "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                        + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests2 "
                        + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                        + "WHERE impid IN (%IMP_ID_LIST%) "
                        + "UNION ALL "
                        + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps2 "
                        + "WHERE impid IN (%IMP_ID_LIST%)",
                0, 0);

        // then
        assertThat(result).isEqualTo("SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                + "WHERE reqid IN (NULL) "
                + "UNION ALL "
                + "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests2 "
                + "WHERE reqid IN (NULL) "
                + "UNION ALL "
                + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                + "WHERE impid IN (NULL) "
                + "UNION ALL "
                + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps2 "
                + "WHERE impid IN (NULL)");
    }
}
