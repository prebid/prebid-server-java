package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.model.Account;

@Builder(toBuilder = true)
@Value
public class SetuidContext {

    @JsonIgnore
    RoutingContext routingContext;

    @JsonIgnore
    UidsCookie uidsCookie;

    @JsonIgnore
    Timeout timeout;

    Account account;

    String cookieName;

    @JsonIgnore
    UsersyncMethodType syncType;

    PrivacyContext privacyContext;

    @JsonIgnore
    GppContext gppContext;

    @JsonIgnore
    ActivityInfrastructure activityInfrastructure;

    public SetuidContext with(GppContext gppContext) {
        return toBuilder().gppContext(gppContext).build();
    }
}
