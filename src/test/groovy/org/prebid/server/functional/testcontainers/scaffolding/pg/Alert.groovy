package org.prebid.server.functional.testcontainers.scaffolding.pg

import com.fasterxml.jackson.core.type.TypeReference
import org.mockserver.model.HttpRequest
import org.prebid.server.functional.model.deals.alert.AlertEvent
import org.prebid.server.functional.testcontainers.scaffolding.NetworkScaffolding
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class Alert extends NetworkScaffolding {

    static final String ALERT_ENDPOINT_PATH = "/deals/alert"

    Alert(MockServerContainer mockServerContainer) {
        super(mockServerContainer, ALERT_ENDPOINT_PATH)
    }

    AlertEvent getRecordedAlertRequest() {
        def body = getRecordedRequestsBody(request).last()
        // 0 index element is returned after deserialization as PBS responses with SingletonList
        decode(body, new TypeReference<List<AlertEvent>>() {})[0]
    }

    Map<String, List<String>> getLastRecordedAlertRequestHeaders() {
        getLastRecordedRequestHeaders(request)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(OK_200.code()))
    }

    @Override
    protected HttpRequest getRequest(String alertId) {
        request().withMethod("POST")
                 .withPath(ALERT_ENDPOINT_PATH)
                 .withBody(jsonPath("\$[?(@.id == '$alertId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        request().withMethod("POST")
                 .withPath(ALERT_ENDPOINT_PATH)
    }
}
