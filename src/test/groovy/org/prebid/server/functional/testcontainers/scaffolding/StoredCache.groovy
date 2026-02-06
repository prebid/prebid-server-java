package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.config.Audience
import org.prebid.server.functional.model.config.AudienceId
import org.prebid.server.functional.model.config.IdentifierType
import org.prebid.server.functional.model.config.OptableTargetingConfig
import org.prebid.server.functional.model.config.TargetingOrtb
import org.prebid.server.functional.model.config.TargetingResult
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.util.PBSUtils

import java.nio.charset.StandardCharsets

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204
import static org.mockserver.model.HttpStatusCode.OK_200

class StoredCache extends NetworkScaffolding {

    private static final String CACHE_ENDPOINT = "/stored-cache"

    StoredCache(WireMockServer mockServerContainer) {
        super(mockServerContainer, CACHE_ENDPOINT)
    }

    @Override
    protected HttpRequest getRequest(String impId) {}

    @Override
    HttpRequest getRequest() {
        request().withMethod("GET")
                .withPath(endpoint)
    }

    @Override
    protected RequestPatternBuilder getRequestPattern() {
        return null
    }

    @Override
    protected RequestPatternBuilder getRequestPattern(String value) {
        return null
    }

    @Override
    void setResponse() {}

    TargetingResult setTargetingResponse(BidRequest bidRequest, OptableTargetingConfig config) {
        def targetingResult = getBodyByRequest(bidRequest)
        mockServerClient.when(request()
                .withMethod("GET")
                .withPath("$endpoint${QueryBuilder.buildQuery(bidRequest, config)}"), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { response().withStatusCode(OK_200.code()).withBody(encode(targetingResult)) }
        targetingResult
    }

    TargetingResult setCachedTargetingResponse(BidRequest bidRequest) {
        def targetingResult = getBodyByRequest(bidRequest)
        mockServerClient.when(request()
                .withMethod("GET")
                .withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { response().withStatusCode(OK_200.code()).withBody(encode(targetingResult)) }
        targetingResult
    }

    void setCachingResponse(HttpStatusCode statusCode = NO_CONTENT_204) {
        mockServerClient.when(request()
                .withMethod("POST")
                .withPath(endpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { response().withStatusCode(statusCode.code()) }
    }

    private static TargetingResult getBodyByRequest(BidRequest bidRequest) {
        new TargetingResult().tap {
            it.audience = [new Audience(ids: [new AudienceId(id: PBSUtils.randomString)], provider: PBSUtils.randomString)]
            it.ortb2 = new TargetingOrtb(user: new User(data: bidRequest.user.data, eids: bidRequest.user.eids))
        }
    }

    private class QueryBuilder {

        static String buildQuery(BidRequest bidRequest, OptableTargetingConfig config) {
            buildIdsString(config) + buildAttributesString(bidRequest, config)
        }

        private static String buildIdsString(OptableTargetingConfig config) {
            def ppids = config.ppidMapping
            if (!ppids) {
                return ''
            }

            def reorderedIds = reorderIds(ppids.keySet(), config.idPrefixOrder)

            reorderedIds.collect { id ->
                def value = ppids[id]
                "&id=${URLEncoder.encode("${id.value}:${value}", StandardCharsets.UTF_8)}"
            }.join('')
        }

        private static Set<IdentifierType> reorderIds(Set<IdentifierType> ids, String idPrefixOrder) {
            if (!idPrefixOrder) {
                return ids
            }
            def prefixOrder = idPrefixOrder.split(',') as List
            def prefixToPriority = prefixOrder.collectEntries { v, i -> [(v): i] }
            ids.sort { prefixToPriority.get(it.value, Integer.MAX_VALUE) }
        }

        private static String buildAttributesString(BidRequest bidRequest, OptableTargetingConfig config) {
            def regs = bidRequest.regs
            def gdpr = regs?.gdpr
            def gdprConsent = bidRequest.user?.consent

            [gdprConsent != null ? "&gdpr_consent=${gdprConsent}" : null,
             "&gdpr=${gdpr ? 1 : 0}",
             regs?.gpp ? "&gpp=${regs.gpp}" : null,
             regs?.gppSid ? "&gpp_sid=${regs.gppSid.first()}" : null,
             config?.timeout ? "&timeout=${config.timeout}ms" : null,
             "&osdk=prebid-server"].findAll().join('')
        }
    }
}
