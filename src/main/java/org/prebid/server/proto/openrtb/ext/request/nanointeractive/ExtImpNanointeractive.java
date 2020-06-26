package org.prebid.server.proto.openrtb.ext.request.nanointeractive;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidRequest.imp[i].ext.nanointeractive
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpNanointeractive {

    String pid;

    List<String> nq;

    String category;

    String subId;

    String ref;
}
