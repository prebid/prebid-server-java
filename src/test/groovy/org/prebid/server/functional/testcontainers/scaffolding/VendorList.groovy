package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.Delay
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.testcontainers.containers.MockServerContainer
import org.wiremock.integrations.testcontainers.WireMockContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion.V2
import static org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion.V3
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.Vendor
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.getDefaultVendorListResponse
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class VendorList extends NetworkScaffolding {

    private static final String VENDOR_LIST_ENDPOINT = "/v{TCF_POLICY}/vendor-list.json"

    VendorList(WireMockContainer wireMockContainer) {
        super(wireMockContainer, VENDOR_LIST_ENDPOINT)
    }

    @Override
    protected HttpRequest getRequest(String value) {
        return null
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(VENDOR_LIST_ENDPOINT)
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
    void reset() {
        TcfPolicyVersion.values().each { version -> super.reset("/v${version.vendorListVersion}/vendor-list.json") }
    }

    void setResponse(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2,
                     Delay delay = null,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)]) {
        def prepareEndpoint = endpoint.replace("{TCF_POLICY}", tcfPolicyVersion.vendorListVersion.toString())
        def prepareEncodeResponseBody = encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = tcfPolicyVersion.vendorListVersion
            it.vendors = vendors
            it.gvlSpecificationVersion = tcfPolicyVersion >= TcfPolicyVersion.TCF_POLICY_V4 ? V3 : V2
        })

        mockServerClient.when(request().withPath(prepareEndpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond { request ->
                    request.withPath(endpoint)
                            ? response().withStatusCode(OK_200.code()).withDelay(delay).withBody(prepareEncodeResponseBody)
                            : HttpResponse.notFoundResponse()
                }
    }
}
