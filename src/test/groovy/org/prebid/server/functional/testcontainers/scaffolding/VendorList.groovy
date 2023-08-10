package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class VendorList extends NetworkScaffolding {

    private static final String VENDOR_LIST_ENDPOINT = "/{TCF_POLICY}/vendor-list.json"

    VendorList(MockServerContainer mockServerContainer) {
        super(mockServerContainer, VENDOR_LIST_ENDPOINT)
    }

    @Override
    protected HttpRequest getRequest(String value) {
        return null
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(VENDOR_LIST_ENDPOINT)
    }

    void setResponse(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2) {
        def prepareEndpoint = endpoint.replace("{TCF_POLICY}", "v" + tcfPolicyVersion.vendorListVersion)
        def prepareEncodeResponseBody = encode(VendorListResponse.defaultVendorListResponse.tap {
            it.vendorListVersion = tcfPolicyVersion.vendorListVersion
        })

        mockServerClient.when(request().withPath(prepareEndpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                        .respond {request -> request.withPath(endpoint)
                                ? response().withStatusCode(OK_200.code()).withBody(prepareEncodeResponseBody)
                                : HttpResponse.notFoundResponse()}
    }
}
