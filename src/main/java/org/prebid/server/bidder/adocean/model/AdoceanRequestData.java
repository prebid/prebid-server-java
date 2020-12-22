package org.prebid.server.bidder.adocean.model;

import io.vertx.core.MultiMap;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class AdoceanRequestData {

    String url;

    MultiMap headers;

    Map<String, String> slaveSizes;
}
