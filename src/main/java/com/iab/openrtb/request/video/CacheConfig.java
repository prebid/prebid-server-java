package com.iab.openrtb.request.video;

import lombok.Value;

@Value(staticConstructor = "of")
public class CacheConfig {

    Integer ttl;
}
