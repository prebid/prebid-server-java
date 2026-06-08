package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.Delay
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.util.privacy.TcfConsent
import org.testcontainers.containers.MockServerContainer

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

    private static final String ENDPOINT_TEMPLATE = "/v{TCF_POLICY}/{FILE_NAME}"

    private final String fileName

    VendorList(MockServerContainer mockServerContainer, String fileName = "vendor-list.json") {
        super(mockServerContainer, ENDPOINT_TEMPLATE.replace("{FILE_NAME}", fileName))
        this.fileName = fileName
    }

    @Override
    protected HttpRequest getRequest(String value) {
        return null
    }

    @Override
    protected HttpRequest getRequest() {
        request().withPath(endpoint)
    }

    @Override
    void reset() {
        TcfPolicyVersion.values().each {
            super.reset("/v${it.vendorListVersion}/${fileName}")
        }
    }

    void setResponse(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2,
                     Delay delay = null,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)],
                     vendorListVersion = TcfConsent.VENDOR_LIST_VERSION) {

        def prepareEndpoint = endpoint.replace("{TCF_POLICY}", tcfPolicyVersion.vendorListVersion.toString())
        def prepareEncodeResponseBody = encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = tcfPolicyVersion.vendorListVersion
            it.vendors = vendors
            it.vendorListVersion = vendorListVersion
            it.gvlSpecificationVersion = tcfPolicyVersion >= TcfPolicyVersion.TCF_POLICY_V4 ? V3 : V2
        })

        def mockResponse = response()
                .withStatusCode(OK_200.code())
                .withBody(prepareEncodeResponseBody)

        if (delay != null) {
            mockResponse.withDelay(delay)
        }

        mockServerClient
                .when(request().withPath(prepareEndpoint), Times.unlimited(), TimeToLive.unlimited(), -10)
                .respond(mockResponse)
    }
}
