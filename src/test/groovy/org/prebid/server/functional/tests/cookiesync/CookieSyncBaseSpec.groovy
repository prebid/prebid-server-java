package org.prebid.server.functional.tests.cookiesync

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.response.cookiesync.CookieSyncResponse
import org.prebid.server.functional.model.response.cookiesync.UserSyncInfo
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.container.PbsContainerConfig
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.AAX
import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.ACUITYADS
import static org.prebid.server.functional.model.bidder.BidderName.ADKERNEL
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.APPNEXUS
import static org.prebid.server.functional.model.bidder.BidderName.BOGUS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.MAGNITE
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.response.cookiesync.UserSyncInfo.Type.REDIRECT
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.MAGNITE_VENDOR_ID

abstract class CookieSyncBaseSpec extends BaseSpec {

    protected static final UserSyncInfo.Type USER_SYNC_TYPE = REDIRECT
    protected static final Integer COOKIE_SYNC_DEFAULT_LIMIT = 2
    protected static final Integer COOKIE_SYNC_MAX_LIMIT = 3
    protected static final String RANDOM_COOKIE_NAME = PBSUtils.randomString

    protected static final String USER_SYNC_URL = "$Dependencies.networkServiceContainer.rootUri/generic-usersync"
    protected static final String ALL_BIDDERS = "*"
    protected static final List<BidderName> DEFAULT_PBS_BIDDERS = [GENERIC, ACEEX, MAGNITE, OPENX, APPNEXUS, AAX, ADKERNEL]

    protected static final Map<String, String> GENERIC_CONFIG = [
            "adapters.${GENERIC.value}.usersync.redirect.url": USER_SYNC_URL + '&redir={redirect_url}',
            "adapters.${GENERIC.value}.meta-info.vendor-id"  : GENERIC_VENDOR_ID as String]
    protected static final Map<String, String> MAGNITE_CONFIG = [
            "adapters.${MAGNITE.value}.enabled"                    : "true",
            "adapters.${MAGNITE.value}.meta-info.vendor-id"        : MAGNITE_VENDOR_ID as String,
            "adapters.${MAGNITE.value}.usersync.cookie-family-name": MAGNITE.value,
            "adapters.${MAGNITE.value}.usersync.redirect.url"      : "https://test.redirect.endpoint.com",
            "adapters.${MAGNITE.value}.usersync.iframe.url"        : "https://test.iframe.endpoint.com&redir={redirect_url}"]
    protected static final Map<String, String> APPNEXUS_CONFIG = [
            "adapters.${APPNEXUS.value}.enabled"                    : "true",
            "adapters.${APPNEXUS.value}.aliases.mediafuse.enabled"  : "false",
            "adapters.${APPNEXUS.value}.usersync.cookie-family-name": APPNEXUS.value,
            "adapters.${APPNEXUS.value}.usersync.redirect.url"      : "https://test.appnexus.redirect.com/getuid?{redirect_url}"]
    private static final Map<String, String> ALIAS_NO_FAMILY_CONFIG = [
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.enabled"                    : "true",
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.meta-info.vendor-id"        : GENERIC_VENDOR_ID as String,
            "adapters.${GENERIC.value}.aliases.${ALIAS.value}.usersync.cookie-family-name": null]
    private static final Map<String, String> COOKIE_SYNC_CONFIG = [
            "cookie-sync.max-limit"    : COOKIE_SYNC_MAX_LIMIT.toString(),
            "cookie-sync.default-limit": COOKIE_SYNC_DEFAULT_LIMIT.toString()]
    private static final Map<String, String> ACEEX_CONFIG = [
            "adapters.${ACEEX.value}.enabled"                    : "true",
            "adapters.${ACEEX.value}.usersync.cookie-family-name": ACEEX.value,
            "adapters.${ACEEX.value}.usersync.redirect.url"      : "https://test.redirect.endpoint.com={redirect_url}"]
    private static final Map<String, String> OPENX_CONFIG = [
            "adapters.${OPENX.value}.enabled"                    : "true",
            "adapters.${OPENX.value}.usersync.cookie-family-name": OPENX.value,
            "adapters.${OPENX.value}.usersync.redirect.url"      : USER_SYNC_URL,
            "adapters.${OPENX.value}.usersync.iframe.url"        : USER_SYNC_URL]
    private static final Map<String, String> BOGUS_CONFIG = ["adapters.${BOGUS.value}.enabled": "true"]
    private static final Map<String, String> AAX_CONFIG = ["adapters.${AAX.value}.enabled": "true"]
    private static final Map<String, String> ACUITYADS_CONFIG = ["adapters.${ACUITYADS.value}.enabled": "false"]
    private static final Map<String, String> ADKERNEL_CONFIG = [
            "adapters.${ADKERNEL.value}.enabled"                  : "true",
            "adapters.${ADKERNEL.value}.aliases.rxnetwork.enabled": "false",
            "adapters.${ADKERNEL.value}.aliases.152media.enabled" : "false"
    ]

    protected static final PbsContainerConfig PBS_CONFIG = new PbsContainerConfig(APPNEXUS_CONFIG + MAGNITE_CONFIG + OPENX_CONFIG +
            GENERIC_CONFIG + ACEEX_CONFIG + AAX_CONFIG + ACUITYADS_CONFIG + ADKERNEL_CONFIG + BOGUS_CONFIG +
            ["cookie-sync.pri": "grid, ix, adkernel"])

    protected static final PbsContainerConfig PBS_WITH_LIMIT_CONFIG = PBS_CONFIG + COOKIE_SYNC_CONFIG

    protected static final PbsContainerConfig PBS_WITH_ALIAS_CONFIG = PBS_CONFIG + ALIAS_NO_FAMILY_CONFIG

    protected static Map<BidderName, UserSyncInfo> getValidBidderUserSyncs(CookieSyncResponse cookieSyncResponse) {
        cookieSyncResponse.bidderStatus
                .findAll { it.userSync }
                .collectEntries { [it.bidder, it.userSync] }
    }

    protected static Map<BidderName, String> getRejectedBidderUserSyncs(CookieSyncResponse cookieSyncResponse) {
        cookieSyncResponse.bidderStatus
                .findAll { it.error }
                .collectEntries { [it.bidder, it.error] }
    }

    protected static List<String> removeExpiresValue(List<String> cookies) {
        cookies.collect { it.replaceFirst(/Expires=[^;]+;/, "Expires=*;") }
    }
}
