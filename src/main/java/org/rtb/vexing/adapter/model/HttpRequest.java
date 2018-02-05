package org.rtb.vexing.adapter.model;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
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

    String uri;

    MultiMap headers;

    BidRequest bidRequest;
}
