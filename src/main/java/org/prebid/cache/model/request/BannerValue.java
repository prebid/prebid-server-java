package org.prebid.cache.model.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class BannerValue {

    String adm;

    String nurl;

    Integer width;

    Integer height;
}
