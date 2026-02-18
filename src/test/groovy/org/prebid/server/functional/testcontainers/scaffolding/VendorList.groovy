package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.any
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static org.prebid.server.functional.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion.V2
import static org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion.V3
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.Vendor
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.getDefaultVendorListResponse
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class VendorList extends NetworkScaffolding {

    private static final String VENDOR_LIST_ENDPOINT = "/v{TCF_POLICY}/vendor-list.json"

    VendorList(NetworkServiceContainer wireMockContainer) {
        super(wireMockContainer, VENDOR_LIST_ENDPOINT)
    }

    @Override
    protected RequestPattern getRequest() {
        anyRequestedFor(urlEqualTo(VENDOR_LIST_ENDPOINT))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String value) {
        return null
    }

    void setResponse(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2,
                     Integer second = 0,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)]) {
        def prepareEndpoint = endpoint.replace("{TCF_POLICY}", tcfPolicyVersion.vendorListVersion.toString())
        def prepareEncodeResponseBody = encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = tcfPolicyVersion.vendorListVersion
            it.vendors = vendors
            it.gvlSpecificationVersion = tcfPolicyVersion >= TcfPolicyVersion.TCF_POLICY_V4 ? V3 : V2
        })

        wireMockClient.register(any(urlMatching(prepareEndpoint))
                .atPriority(Integer.MAX_VALUE)
                .willReturn(aResponse()
                        .withStatus(OK_200.code)
                        .withFixedDelay(second * 1000)
                        .withBody(prepareEncodeResponseBody)))
    }
}
