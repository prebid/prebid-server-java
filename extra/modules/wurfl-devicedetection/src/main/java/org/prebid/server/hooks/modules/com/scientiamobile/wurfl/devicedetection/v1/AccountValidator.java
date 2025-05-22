package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.settings.model.Account;
import lombok.Builder;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;

@Slf4j
@Builder
public class AccountValidator {

    Map<String, String> allowedPublisherIds;
    AuctionContext auctionContext;

    public boolean isAccountValid() {

        return Optional.ofNullable(auctionContext)
                .map(AuctionContext::getAccount)
                .map(Account::getId)
                .filter(StringUtils::isNotBlank)
                .map(allowedPublisherIds::get)
                .filter(Objects::nonNull)
                .isPresent();
    }

}
