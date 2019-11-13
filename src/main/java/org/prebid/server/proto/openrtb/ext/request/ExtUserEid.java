package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.user.ext.eids[]
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserEid {

    String source;

    String id;

    List<ExtUserEidUid> uids;

    ObjectNode ext;
}
