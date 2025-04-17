package org.prebid.server.hooks.modules.optable.targeting.model;

import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@AllArgsConstructor(staticName = "of")
public class CachingKey {

    String tenant;

    String origin;

    Query query;

    List<String> ips;

    public String toString() {
        return "%s:%s:%s:%s".formatted(
                tenant,
                origin,
                CollectionUtils.isNotEmpty(ips) ? ips.getFirst() : "none",
                query.getIds());
    }

    public String toEncodedString() {
        return "%s:%s:%s:%s".formatted(
                tenant,
                origin,
                CollectionUtils.isNotEmpty(ips) ? ips.getFirst() : "none",
                URLEncoder.encode(query.getIds(), StandardCharsets.UTF_8));
    }
}
