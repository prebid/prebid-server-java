package org.prebid.server.gdpr.model;

public enum GdprResult {

    error_invalid_gdpr,
    error_missing_consent,
    error_invalid_consent,

    restricted, // cookies or local storage is prohibited
    allowed // cookies or local storage can be used (consent string was processed successfully)
}
