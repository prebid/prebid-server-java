package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.user.ext.eids[].uids[]
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserEidUid {

    String id;

    ExtUserEidUidExt ext;
}
