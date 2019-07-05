package org.prebid.server.proto.openrtb.ext.request.visx;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.visx
 */
@AllArgsConstructor(
        staticName = "of"
)
@Value
public class ExtImpVisx {
    Integer uid;

    List<Integer> size;
}
