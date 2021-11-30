package org.prebid.server.functional.testcontainers.scaffolding.pg

import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.deals.userdata.UserDetailsRequest
import org.prebid.server.functional.model.deals.userdata.UserDetailsResponse
import org.prebid.server.functional.model.deals.userdata.WinEventNotification
import org.prebid.server.functional.testcontainers.scaffolding.NetworkScaffolding
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.testcontainers.containers.MockServerContainer

import static org.mockserver.model.HttpRequest.request
import static org.mockserver.model.HttpResponse.response
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.mockserver.model.JsonPathBody.jsonPath

class UserData extends NetworkScaffolding {

    static final String USER_DETAILS_ENDPOINT_PATH = "/deals/user-details"
    static final String WIN_EVENT_ENDPOINT_PATH = "/deals/win-event"

    UserData(MockServerContainer mockServerContainer, ObjectMapperWrapper mapper) {
        super(mockServerContainer, WIN_EVENT_ENDPOINT_PATH, mapper)
    }

    UserDetailsRequest getRecordedUserDetailsRequest() {
        def body = getRecordedRequestsBody(userDetailsRequest).last()
        mapper.decode(body, UserDetailsRequest)
    }

    WinEventNotification getRecordedWinEventRequest() {
        def body = getRecordedRequestsBody(request).last()
        mapper.decode(body, WinEventNotification)
    }

    void setUserDataResponse(UserDetailsResponse userDataResponse, HttpStatusCode httpStatusCode = OK_200) {
        resetUserDetailsEndpoint()
        setResponse(userDetailsRequest, userDataResponse, httpStatusCode)
    }

    int getRecordedUserDetailsRequestCount() {
        getRequestCount(userDetailsRequest)
    }

    void resetUserDetailsEndpoint() {
        reset(USER_DETAILS_ENDPOINT_PATH)
    }

    @Override
    void setResponse() {
        mockServerClient.when(request().withPath(endpoint))
                        .respond(response().withStatusCode(OK_200.code()))
    }

    @Override
    protected HttpRequest getRequest(String bidId) {
        request().withMethod("POST")
                 .withPath(WIN_EVENT_ENDPOINT_PATH)
                 .withBody(jsonPath("\$[?(@.bidId == '$bidId')]"))
    }

    @Override
    protected HttpRequest getRequest() {
        request().withMethod("POST")
                 .withPath(WIN_EVENT_ENDPOINT_PATH)
    }

    private static HttpRequest getUserDetailsRequest() {
        request().withMethod("POST")
                 .withPath(USER_DETAILS_ENDPOINT_PATH)
    }
}
