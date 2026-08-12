package org.prebid.server.functional.testcontainers.scaffolding

import com.github.tomakehurst.wiremock.matching.RequestPattern
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.prebid.server.functional.testcontainers.container.NetworkServiceContainer
import org.prebid.server.functional.util.privacy.TcfConsent

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.any
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
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

    VendorList(NetworkServiceContainer networkServiceContainer, String fileName = "vendor-list.json") {
        super(networkServiceContainer, ENDPOINT_TEMPLATE.replace("{FILE_NAME}", fileName))
        this.fileName = fileName
    }

    @Override
    protected RequestPattern getRequest(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2) {
        anyRequestedFor(urlEqualTo(endpoint.replace("{TCF_POLICY}", tcfPolicyVersion.vendorListVersion.toString())))
                .build()
    }

    @Override
    protected RequestPatternBuilder getRequest(String value) {
        throw new UnsupportedOperationException()
    }

    void setResponse(TcfPolicyVersion tcfPolicyVersion = TCF_POLICY_V2,
                     Integer delaySeconds = null,
                     Map<Integer, Vendor> vendors = [(GENERIC_VENDOR_ID): Vendor.getDefaultVendor(GENERIC_VENDOR_ID)],
                     Integer vendorListVersion = TcfConsent.VENDOR_LIST_VERSION,
                     Integer times = Integer.MAX_VALUE) {

        def preparedEndpoint = endpoint.replace("{TCF_POLICY}", tcfPolicyVersion.vendorListVersion.toString())

        def preparedEncodedResponseBody = encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = tcfPolicyVersion.vendorListVersion
            it.vendors = vendors
            it.vendorListVersion = vendorListVersion
            it.gvlSpecificationVersion = tcfPolicyVersion >= TcfPolicyVersion.TCF_POLICY_V4 ? V3 : V2
        })

        def response = aResponse()
                .withStatus(SC_OK)
                .withBody(preparedEncodedResponseBody)

        if (delaySeconds != null) {
            response.withFixedDelay(delaySeconds * 1000)
        }

        if (times == Integer.MAX_VALUE) {
            wireMockClient.register(any(urlMatching(preparedEndpoint))
                    .atPriority(Integer.MAX_VALUE)
                    .willReturn(response))
            return
        }

        def scenario = UUID.randomUUID().toString()
        for (int i = 0; i < times; i++) {
            wireMockClient.register(any(urlMatching(preparedEndpoint))
                            .atPriority(Integer.MAX_VALUE)
                            .inScenario(scenario)
                            .whenScenarioStateIs(i == 0 ? STARTED : "STATE_$i")
                            .willSetStateTo("STATE_${i + 1}")
                            .willReturn(response)
            )
        }
    }
}
