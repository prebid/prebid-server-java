package org.prebid.server.it;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;

public class CacheResponseTransformer extends ResponseTransformer {

    @Override
    public com.github.tomakehurst.wiremock.http.Response transform(
            Request request, com.github.tomakehurst.wiremock.http.Response response, FileSource files,
            Parameters parameters) {

        return com.github.tomakehurst.wiremock.http.Response.response().body("\"id\":132").status(200).build();
    }

    @Override
    public String getName() {
        return "cache-response-transformer";
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

}
