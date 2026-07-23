package org.prebid.server.functional.testcontainers.scaffolding

import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.model.Delay
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.util.privacy.TcfConsent
import org.testcontainers.containers.MockServerContainer
import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.any
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static org.apache.http.HttpStatus.SC_OK
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
    protected RequestPattern getRequest() {
        anyRequestedFor(urlEqualTo(VENDOR_LIST_ENDPOINT))
                .build()
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
                     Integer second = 0,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)],
                     Integer vendorListVersion = TcfConsent.VENDOR_LIST_VERSION,
                     Times times = Times.unlimited()) {

                     Integer second = 0,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)]) {
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

        delay?.with {
            mockResponse.withDelay(it)
        }

        mockServerClient
                .when(request().withPath(prepareEndpoint), times, TimeToLive.unlimited(), -10)
                .respond(mockResponse)
        wireMockClient.register(any(urlMatching(prepareEndpoint))
                .atPriority(Integer.MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withFixedDelay(second * 1000)
                        .withBody(prepareEncodeResponseBody)))
    }
}
