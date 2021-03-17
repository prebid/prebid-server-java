package org.prebid.server.settings.bidder;

import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Value
public class PlatformInfo {

    boolean enabled;

    MediaTypeMappings supportedMediaTypes;

    MediaTypeMappings notSupportedMediaTypes;

    public static PlatformInfo create(List<String> mediaTypes) {
        return CollectionUtils.isEmpty(mediaTypes)
                ? new PlatformInfo(false, MediaTypeMappings.EMPTY, MediaTypeMappings.ALL)
                : new PlatformInfo(
                        true,
                        MediaTypeMappings.byNames(mediaTypes),
                        MediaTypeMappings.negateNames(mediaTypes));
    }
}
