package org.prebid.server.functional.testcontainers.scaffolding.pg

import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.deals.report.DeliveryStatisticsReport
import org.prebid.server.functional.testcontainers.scaffolding.NetworkScaffolding
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.ClearType.ALL
import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class DeliveryStatistics extends NetworkScaffolding {

    static final String REPORT_DELIVERY_ENDPOINT_PATH = "/deals/report/delivery"

    DeliveryStatistics(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, REPORT_DELIVERY_ENDPOINT_PATH, mapper)
    }

    Map<String, String> getLastRecordedDeliveryRequestHeaders() {
        getLastRecordedRequestHeaders(request)
    }

    DeliveryStatisticsReport getLastRecordedDeliveryStatisticsReportRequest() {
        recordedDeliveryStatisticsReportRequests.last()
    }

    void resetRecordedRequests() {
        reset(REPORT_DELIVERY_ENDPOINT_PATH, ALL)
    }

    void setResponse(HttpStatusCode statusCode = OK_200) {
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(statusCode.code()))
    }

    List<DeliveryStatisticsReport> getRecordedDeliveryStatisticsReportRequests() {
        def body = getRecordedRequestsBody(request)
        body.collect { mapper.decode(it, DeliveryStatisticsReport) }
    }

    @Override
    protected HttpRequest getRequest(String reportId) {
        request().withMethod("POST")
                 .withPath(REPORT_DELIVERY_ENDPOINT_PATH)
                 .withBody(jsonPath("\$[?(@.reportId == '$reportId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        request().withMethod("POST")
                 .withPath(REPORT_DELIVERY_ENDPOINT_PATH)
    }
}
