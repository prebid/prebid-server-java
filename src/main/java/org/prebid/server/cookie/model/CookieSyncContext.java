package org.prebid.server.cookie.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.bidder.UsersyncMethodChooser;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.settings.model.Account;

import java.util.List;

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

    @JsonIgnore
    GppContext gppContext;

    PrivacyContext privacyContext;

    @JsonIgnore
    ActivityInfrastructure activityInfrastructure;

    @JsonIgnore
    int limit;

    @JsonIgnore
    boolean debug;

    @JsonIgnore
    List<String> warnings;

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
