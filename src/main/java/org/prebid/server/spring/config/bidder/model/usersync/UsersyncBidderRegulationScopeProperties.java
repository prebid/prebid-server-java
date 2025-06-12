package org.prebid.server.spring.config.bidder.model.usersync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UsersyncBidderRegulationScopeProperties {

    boolean gdpr;

    List<Integer> gppSid;
}
