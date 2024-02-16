package org.prebid.server.cookie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.model.PartitionedCookie;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPrivacySandboxConfig;
import org.prebid.server.settings.model.AccountPrivacySandboxCookieDeprecationConfig;
import org.prebid.server.util.HttpUtil;

import java.util.Optional;

public class CookieDeprecationService {

    private static final String COOKIE_NAME = "receive-cookie-deprecation";
    private static final String DEVICE_EXT_COOKIE_DEPRECATION_FIELD_NAME = "cdep";
    private static final long DEFAULT_MAX_AGE = 604800L;

    private final Account defaultAccount;

    public CookieDeprecationService(String defaultAccountConfig, JacksonMapper mapper) {
        this.defaultAccount = parseAccount(defaultAccountConfig, mapper);
    }

    private static Account parseAccount(String accountConfig, JacksonMapper mapper) {
        try {
            final Account account = StringUtils.isNotBlank(accountConfig)
                    ? mapper.decodeValue(accountConfig, Account.class)
                    : null;

            return ObjectUtils.isNotEmpty(account) ? account : null;
        } catch (DecodeException e) {
            throw new IllegalArgumentException("Could not parse default account configuration", e);
        }
    }

    public PartitionedCookie makeCookie(Account account, RoutingContext routingContext) {
        final Account resolvedAccount = account.isEmpty() ? defaultAccount : account;

        if (hasDeprecationCookieInRequest(routingContext) || isCookieDeprecationDisabled(resolvedAccount)) {
            return null;
        }

        final Long maxAge = getCookieDeprecationConfig(resolvedAccount)
                .map(AccountPrivacySandboxCookieDeprecationConfig::getTtlSec)
                .orElse(DEFAULT_MAX_AGE);

        return PartitionedCookie.of(Cookie.cookie(COOKIE_NAME, "1")
                .setPath("/")
                .setSameSite(CookieSameSite.NONE)
                .setSecure(true)
                .setHttpOnly(true)
                .setMaxAge(maxAge));
    }

    private static boolean hasDeprecationCookieInRequest(RoutingContext routingContext) {
        return HttpUtil.cookiesAsMap(routingContext).containsKey(COOKIE_NAME);
    }

    public BidRequest updateBidRequestDevice(BidRequest bidRequest, AuctionContext auctionContext) {
        final String secCookieDeprecation = auctionContext.getHttpRequest()
                .getHeaders()
                .get(HttpUtil.SEC_COOKIE_DEPRECATION);

        final Account account = auctionContext.getAccount();
        final Account resolvedAccount = account.isEmpty() ? defaultAccount : account;
        final Device device = bidRequest.getDevice();

        if (secCookieDeprecation == null
                || containsCookieDeprecation(device)
                || isCookieDeprecationDisabled(resolvedAccount)) {

            return bidRequest;
        }

        if (secCookieDeprecation.length() > 100) {
            auctionContext.getDebugWarnings().add(HttpUtil.SEC_COOKIE_DEPRECATION + " header has invalid value");
            return bidRequest;
        }

        final ExtDevice extDevice = Optional.ofNullable(device).map(Device::getExt).orElse(ExtDevice.empty());
        extDevice.addProperty(DEVICE_EXT_COOKIE_DEPRECATION_FIELD_NAME, TextNode.valueOf(secCookieDeprecation));

        final Device resolvedDevice = Optional.ofNullable(device)
                .map(Device::toBuilder)
                .orElse(Device.builder())
                .ext(extDevice)
                .build();

        return bidRequest.toBuilder().device(resolvedDevice).build();
    }

    private boolean containsCookieDeprecation(Device device) {
        return Optional.ofNullable(device)
                .map(Device::getExt)
                .map(ext -> ext.getProperty(DEVICE_EXT_COOKIE_DEPRECATION_FIELD_NAME))
                .map(JsonNode::asText)
                .isPresent();
    }

    private static Optional<AccountPrivacySandboxCookieDeprecationConfig> getCookieDeprecationConfig(Account account) {
        return Optional.ofNullable(account.getAuction())
                .map(AccountAuctionConfig::getPrivacySandbox)
                .map(AccountPrivacySandboxConfig::getCookieDeprecation);
    }

    private static boolean isCookieDeprecationDisabled(Account account) {
        return getCookieDeprecationConfig(account)
                .map(AccountPrivacySandboxCookieDeprecationConfig::getEnabled)
                .map(BooleanUtils::isNotTrue)
                .orElse(true);
    }
}
