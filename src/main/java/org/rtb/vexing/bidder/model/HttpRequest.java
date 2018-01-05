package org.rtb.vexing.bidder.model;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Packages together the fields needed to make an http request.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class HttpRequest {

    HttpMethod method;

    String uri;

    String body;

    MultiMap headers;
}
