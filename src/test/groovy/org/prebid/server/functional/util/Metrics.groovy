package org.prebid.server.functional.util

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.ModuleHookImplementation
import org.prebid.server.functional.model.config.ModuleName
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.request.auction.ActivityType

class Metrics {

    static class General {

        static String alert() {
            'alerts.general'
        }

        static String impsRequested() {
            'imps_requested'
        }

        static String impsDropped() {
            'imps_dropped'
        }

        static String debugRequests() {
            'debug_requests'
        }

        static String floorsFetchFailure() {
            'price-floors.fetch.failure'
        }

        static String storedRequestFound() {
            'stored_requests_found'
        }

        static String geolocationRequests() {
            'geolocation_requests'
        }

        static String geolocationFail() {
            'geolocation_fail'
        }

        static String geolocationSuccessful() {
            'geolocation_successful'
        }

        static String requestOk(ChannelType channel) {
            "requests.ok.openrtb2-${channel.value}"
        }
    }

    static class Module {

        static String call(ModuleName name, Stage stage) {
            getBasicModuleMetric(name, stage, 'call')
        }

        static String noop(ModuleName name, Stage stage) {
            getBasicModuleMetric(name, stage, 'success.noop')
        }

        static String update(ModuleName name, Stage stage) {
            getBasicModuleMetric(name, stage, 'success.update')
        }

        static String noInvocation(ModuleName name, Stage stage) {
            getBasicModuleMetric(name, stage, 'success.no-invocation')
        }

        static String executionError(ModuleName name, Stage stage) {
            getBasicModuleMetric(name, stage, 'execution-error')
        }

        private static String getBasicModuleMetric(ModuleName name, Stage stage, String suffix) {
            def hook = ModuleHookImplementation.forValue(name, stage).code
            "modules.module.${name.code}.stage.${stage.metricValue}.hook.${hook}.${suffix}"
        }
    }

    static class Cache {

        static String requestsOk() {
            'prebid_cache.requests.ok'
        }

        static String creativeSizeJson() {
            'prebid_cache.creative_size.json'
        }

        static String creativeSizeXml() {
            'prebid_cache.creative_size.xml'
        }

        static String creativeTtlJson() {
            'prebid_cache.creative_ttl.json'
        }

        static String creativeTtlXml() {
            'prebid_cache.creative_ttl.xml'
        }

        static String accountCreativeSizeJson(String accountId) {
            accountMetric(accountId, "creative_size.json")
        }

        static String accountCreativeSizeXml(String accountId) {
            accountMetric(accountId, "creative_size.xml")
        }

        static String accountCreativeTtlJson(String accountId) {
            accountMetric(accountId, "creative_ttl.json")
        }

        static String accountCreativeTtlXml(String accountId) {
            accountMetric(accountId, "creative_ttl.xml")
        }

        static String accountRequestsOk(String accountId) {
            accountMetric(accountId, "requests.ok")
        }

        static String creativeSizeText(ModuleName name) {
            moduleStorageMetric(name, "entry_size.text")
        }

        static String creativeTtlText(ModuleName name) {
            moduleStorageMetric(name, "entry_ttl.text")
        }

        static String readOk(ModuleName name) {
            moduleStorageMetric(name, "read.ok")
        }

        static String readErr(ModuleName name) {
            moduleStorageMetric(name, "read.err")
        }

        static String writeOk(ModuleName name) {
            moduleStorageMetric(name, "write.ok")
        }

        static String writeErr(ModuleName name) {
            moduleStorageMetric(name, "write.err")
        }

        static String vtrackCreativeSizeXml() {
            vtrackMetric("creative_size.xml")
        }

        static String vtrackCreativeTtlXml() {
            vtrackMetric("creative_ttl.xml")
        }

        static String vtrackWriteOk() {
            vtrackMetric("write.ok")
        }

        static String vtrackWriteErr() {
            vtrackMetric("write.err")
        }

        static String vtrackReadOk() {
            vtrackMetric("read.ok")
        }

        static String vtrackReadErr() {
            vtrackMetric("read.err")
        }

        private static String accountMetric(String accountId, String suffix) {
            "account.${accountId}.prebid_cache.${suffix}"
        }

        private static String moduleStorageMetric(ModuleName name, String suffix) {
            "prebid_cache.module_storage.${name.code}.${suffix}"
        }

        private static String vtrackMetric(String suffix) {
            "prebid_cache.vtrack.${suffix}"
        }
    }

    static class Account {

        static String invalidConfigFloors(String accountId) {
            "alerts.account_config.${accountId}.price-floors"
        }

        static String requestType(String accountId, ChannelType channel) {
            baseMetric(accountId, "requests.type.openrtb2-${channel.value}")
        }

        static String debugRequests(String accountId) {
            baseMetric(accountId, "debug_requests")
        }

        static String cacheVtrackCreativeSizeXml(String accountId) {
            baseMetric(accountId, "prebid_cache.vtrack.creative_size.xml")
        }

        static String cacheVtrackCreativeTtlXml(String accountId) {
            baseMetric(accountId, "prebid_cache.vtrack.creative_ttl.xml")
        }

        static String cacheVtrackWriteOk(String accountId) {
            baseMetric(accountId, "prebid_cache.vtrack.write.ok")
        }

        static String cacheVtrackWriteErr(String accountId) {
            baseMetric(accountId, "prebid_cache.vtrack.write.err")
        }

        static String profilesLimitExceeded(String accountId) {
            baseMetric(accountId, "profiles.limit_exceeded")
        }

        static String profilesMissing(String accountId) {
            baseMetric(accountId, "profiles.missing")
        }

        static String rejectedInvalidAccount(String accountId) {
            baseMetric(accountId, "requests.rejected.invalid-account")
        }

        static String requests(String accountId) {
            baseMetric(accountId, "requests")
        }

        static String buyerUidScrubbed(String accountId, BidderName bidder) {
            adapterMetric(accountId, bidder, "requests.buyeruid_scrubbed")
        }

        static String bidsReceived(String accountId, BidderName bidder) {
            adapterMetric(accountId, bidder, "bids_received")
        }

        static String prices(String accountId, BidderName bidder) {
            adapterMetric(accountId, bidder, "prices")
        }

        static String requestTime(String accountId, BidderName bidder) {
            adapterMetric(accountId, bidder, "request_time")
        }

        static String requestsGotBids(String accountId, BidderName bidder) {
            adapterMetric(accountId, bidder, "requests.gotbids")
        }

        static String validationSizeWarn(String accountId) {
            baseMetric(accountId, "response.validation.size.warn")
        }

        static String validationSizeError(String accountId) {
            baseMetric(accountId, "response.validation.size.err")
        }

        static String validationSecureWarn(String accountId) {
            baseMetric(accountId, "response.validation.secure.warn")
        }

        static String validationSecureError(String accountId) {
            baseMetric(accountId, "response.validation.secure.err")
        }

        private static String adapterMetric(String accountId, BidderName bidder, String suffix) {
            baseMetric(accountId, "adapter.${bidder.value}.${suffix}")
        }

        private static String baseMetric(String accountId, String suffix) {
            "account.${accountId}.${suffix}"
        }
    }

    static class Adapter {

        private static String request(BidderName adapter, String suffix) {
            metric(adapter, 'requests', suffix)
        }

        static String seat(BidderName bidder) {
            response(bidder, 'seat')
        }

        static String validationSeat(BidderName bidder) {
            response(bidder, 'validation.seat')
        }

        static String requestType(BidderName bidder, ChannelType channel) {
            request(bidder, "type.openrtb2-${channel.value}")
        }

        static String buyerUidScrubbed(BidderName bidder) {
            request(bidder, 'buyeruid_scrubbed')
        }

        static String validationSizeWarn(BidderName bidder) {
            response(bidder, 'validation.size.warn')
        }

        static String validationSizeError(BidderName bidder) {
            response(bidder, 'validation.size.err')
        }

        static String validationSecureWarn(BidderName bidder) {
            response(bidder, 'validation.secure.warn')
        }

        static String bidValidation(BidderName bidder) {
            request(bidder, 'bid_validation')
        }

        static String validationSecureError(BidderName bidder) {
            response(bidder, 'validation.secure.err')
        }

        private static String response(BidderName adapter, String suffix) {
            metric(adapter, 'response', suffix)
        }

        private static String metric(BidderName adapter, String type, String suffix) {
            "adapter.${adapter.value}.${type}.${suffix}"
        }
    }

    static class Privacy {

        static String tcfVendorListMissing(Integer version) {
            tcfMetric(version, 'vendorlist.missing')
        }

        static String tcfInGeo(Integer version) {
            tcfMetric(version, 'in-geo')
        }

        static String tcfOutGeo(Integer version) {
            tcfMetric(version, 'out-geo')
        }

        static String requestProcessedActivityCount() {
            processedRulesMetric('requests')
        }

        static String requestDisallowedActivityCount(ActivityType activity) {
            activityMetric('requests', activity)
        }

        static String adapterDisallowedActivityCount(BidderName bidder, ActivityType activity) {
            activityMetric("adapter.${bidder.value}", activity)
        }

        static String accountDisallowedActivityCount(String accountId, ActivityType activity) {
            activityMetric("account.${accountId}", activity)
        }

        static String accountProcessedRulesCount(String accountId) {
            processedRulesMetric("account.${accountId}")
        }

        private static String activityMetric(String prefix, ActivityType activity) {
            "${prefix}.activity.${activity.metricValue}.disallowed.count"
        }

        private static String processedRulesMetric(String prefix) {
            "${prefix}.activity.processedrules.count"
        }

        private static String tcfMetric(Integer version, String suffix) {
            "privacy.tcf.v${version}.${suffix}"
        }
    }

    static class CookieSync {

        static String tcfBlocked(BidderName adapter) {
            cookieBaseMetric(adapter, 'tcf.blocked')
        }

        static String filtered(BidderName adapter) {
            cookieBaseMetric(adapter, 'filtered')
        }

        private static String cookieBaseMetric(BidderName adapter, String suffix) {
            "cookie_sync.${adapter.value}.${suffix}"
        }
    }

    static class UserSync {

        static String sizeBlocked(BidderName adapter) {
            userSyncBaseMetric(adapter, 'sizeblocked')
        }

        static String sets(BidderName adapter) {
            userSyncBaseMetric(adapter, 'sets')
        }

        static String tcfBlocked(BidderName adapter) {
            userSyncBaseMetric(adapter, 'tcf.blocked')
        }

        static String sizedOut(BidderName adapter) {
            userSyncBaseMetric(adapter, 'sizedout')
        }

        private static String userSyncBaseMetric(BidderName adapter, String suffix) {
            "usersync.${adapter.value}.${suffix}"
        }
    }
}
