package org.prebid.server.spring.config.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class CacheDefaultTtlProperties {

    Integer bannerTtl;

    Integer videoTtl;

    Integer audioTtl;

    Integer nativeTtl;
}
