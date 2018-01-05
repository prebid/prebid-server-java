package org.rtb.vexing.bidder.model;

import io.vertx.core.MultiMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Packages together information from the server's http response.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class HttpResponse {

    int statusCode;

    MultiMap headers;

    String body;
}
