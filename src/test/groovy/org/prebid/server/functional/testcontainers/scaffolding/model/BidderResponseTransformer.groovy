package org.prebid.server.functional.testcontainers.scaffolding.model

import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ResponseTransformer
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response

class BidderResponseTransformer extends ResponseTransformer {

    @Override
    Response transform(Request request, Response response, FileSource files, Parameters parameters) {
        return Response.response().body("{\n" +
                "  \"name\": \"John Doe\",\n" +
                "  \"age\": 30,\n" +
                "  \"isStudent\": false,\n" +
                "  \"car\": null\n" +
                "}").build()
    }

    @Override
    String getName() {
        return "BidderResponseTransformer"; // <-- саме таке ім’я треба використовувати
    }

    @Override
    boolean applyGlobally() {
        return false; // трансформер застосовується тільки для stub з transformers("dynamic-body-transformer")
    }

    @Override
    void start() {
        super.start()
    }

    @Override
    void stop() {
        super.stop()
    }
}
