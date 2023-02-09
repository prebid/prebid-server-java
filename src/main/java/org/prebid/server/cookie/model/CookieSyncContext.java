package org.prebid.server.cookie.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.UsersyncMethodChooser;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;

@Builder(toBuilder = true)
@Value
public class CookieSyncContext {

    @JsonIgnore
    RoutingContext routingContext;

    @JsonIgnore
    UidsCookie uidsCookie;

    CookieSyncRequest cookieSyncRequest;

    @JsonIgnore
    BiddersContext biddersContext;

    @JsonIgnore
    UsersyncMethodChooser usersyncMethodChooser;

    @JsonIgnore
    Timeout timeout;

    Account account;

    PrivacyContext privacyContext;

    @JsonIgnore
    int limit;

    @JsonIgnore
    boolean debug;

    public CookieSyncContext with(CookieSyncRequest cookieSyncRequest) {
        return toBuilder().cookieSyncRequest(cookieSyncRequest).build();
    }

    public CookieSyncContext with(BiddersContext biddersContext) {
        return toBuilder().biddersContext(biddersContext).build();
    }

    public CookieSyncContext with(Account account) {
        return toBuilder().account(account).build();
    }

    public CookieSyncContext with(PrivacyContext privacyContext) {
        return toBuilder().privacyContext(privacyContext).build();
    }

    public CookieSyncContext with(int limit) {
        return toBuilder().limit(limit).build();
    }
}
