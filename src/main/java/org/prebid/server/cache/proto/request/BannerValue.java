package org.prebid.server.cache.proto.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class BannerValue {

    String adm;

    String nurl;

    Integer width;

    Integer height;
}
