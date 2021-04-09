package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
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

    PrivacyContext privacyContext;
}
