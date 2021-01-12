package org.prebid.server.settings.jdbc;

import io.vertx.ext.sql.ResultSet;
import org.prebid.server.settings.jdbc.model.SqlQuery;
import org.prebid.server.settings.model.Account;

public interface AccountQueryTranslator {

    SqlQuery selectQuery(String accountId);

    Account translateQueryResult(ResultSet result);
}
