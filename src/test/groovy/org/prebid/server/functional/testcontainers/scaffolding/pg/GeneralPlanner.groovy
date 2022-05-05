package org.prebid.server.functional.testcontainers.scaffolding.pg

import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.deals.register.RegisterRequest
import org.prebid.server.functional.model.mock.services.generalplanner.PlansResponse
import org.prebid.server.functional.testcontainers.scaffolding.NetworkScaffolding
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class GeneralPlanner extends NetworkScaffolding {

    static final String PLANS_ENDPOINT_PATH = "/deals/plans"
    static final String REGISTER_ENDPOINT_PATH = "/deals/register"

    GeneralPlanner(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, REGISTER_ENDPOINT_PATH, mapper)
    }

    void initRegisterResponse(HttpStatusCode statusCode = OK_200) {
        reset()
        setResponse(statusCode)
    }

    void initPlansResponse(PlansResponse plansResponse,
                           HttpStatusCode statusCode = OK_200,
                           Times times = Times.exactly(1)) {
        resetPlansEndpoint()
        setPlansResponse(plansResponse, statusCode, times)
    }

    void resetPlansEndpoint() {
        reset(PLANS_ENDPOINT_PATH)
    }

    int getRecordedPlansRequestCount() {
        getRequestCount(plansRequest)
    }

    RegisterRequest getLastRecordedRegisterRequest() {
        recordedRegisterRequests.last()
    }

    List<RegisterRequest> getRecordedRegisterRequests() {
        def body = getRecordedRequestsBody(request)
        body.collect { mapper.decode(it, RegisterRequest) }
    }

    void setResponse(HttpStatusCode statusCode = OK_200) {
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(statusCode.code()))
    }

    Map<String, List<String>> getLastRecordedRegisterRequestHeaders() {
        getLastRecordedRequestHeaders(request)
    }

    Map<String, List<String>> getLastRecordedPlansRequestHeaders() {
        getLastRecordedRequestHeaders(plansRequest)
    }

    @Override
    void reset() {
        super.reset(PLANS_ENDPOINT_PATH)
        super.reset(REGISTER_ENDPOINT_PATH)
    }

    private void setPlansResponse(PlansResponse plansResponse,
                                  HttpStatusCode statusCode,
                                  Times times = Times.exactly(1)) {
        setResponse(plansRequest, plansResponse, statusCode, times)
    }

    @Override
    protected HttpRequest getRequest(String hostInstanceId) {
        request().withMethod("POST")
                 .withPath(REGISTER_ENDPOINT_PATH)
                 .withBody(jsonPath("\$[?(@.hostInstanceId == '$hostInstanceId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        request().withMethod("POST")
                 .withPath(REGISTER_ENDPOINT_PATH)
    }

    private static HttpRequest getPlansRequest() {
        request().withMethod("GET")
                 .withPath(PLANS_ENDPOINT_PATH)
    }
}
