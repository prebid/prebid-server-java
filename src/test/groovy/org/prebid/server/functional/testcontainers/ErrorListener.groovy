package org.prebid.server.functional.testcontainers

import io.qameta.allure.Attachment
import io.restassured.RestAssured
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.model.ErrorInfo
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo

class ErrorListener extends AbstractRunListener {

    ByteArrayOutputStream request = new ByteArrayOutputStream()
    ByteArrayOutputStream response = new ByteArrayOutputStream()
    PrintStream requestVar = new PrintStream(request, true)
    PrintStream responseVar = new PrintStream(response, true)

    @Override
    void beforeSpec(SpecInfo spec) {
        RestAssured.filters(new RequestLoggingFilter(requestVar))
        RestAssured.filters(new ResponseLoggingFilter(responseVar))
    }

    @Override
    void beforeIteration(IterationInfo iteration) {
        request.reset()
    }

    @Override
    void error(ErrorInfo error) {
        logRequest(request)
        logResponse(response)
    }

    @Attachment(value = "request")
    private static byte[] logRequest(ByteArrayOutputStream stream) {
        attach(stream)
    }

    @Attachment(value = "response")
    private static byte[] logResponse(ByteArrayOutputStream stream) {
        attach(stream)
    }

    private static byte[] attach(ByteArrayOutputStream log) {
        byte[] array = log.toByteArray()
        log.reset()
        array
    }
}
