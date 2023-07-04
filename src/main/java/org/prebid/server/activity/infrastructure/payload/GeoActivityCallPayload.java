package org.prebid.server.activity.infrastructure.payload;

public interface GeoActivityCallPayload extends ActivityCallPayload {

    String country();

    String region();
}
