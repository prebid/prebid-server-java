package org.prebid.server.proto.openrtb.ext.request.flipp;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(toBuilder = true)
@Getter
public class ExtImpFlipp {

    String publisherNameIdentifier;

    String creativeType;

    Integer siteId;

    List<Integer> zoneIds;

    String userKey;

    String ip;

    ExtImpFlippOptions options;
}
