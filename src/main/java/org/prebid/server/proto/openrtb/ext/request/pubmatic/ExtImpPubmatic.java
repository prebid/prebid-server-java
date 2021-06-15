package org.prebid.server.proto.openrtb.ext.request.pubmatic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.pubmatic
 * PublisherId and adSlot are mandatory parameters, others are optional parameters
 * Keywords is bid specific parameter,
 * WrapExt needs to be sent once per bid request
 */
@Builder
@Value
public class ExtImpPubmatic {

    String publisherId;

    String adSlot;

    ObjectNode wrapper;

    List<ExtImpPubmaticKeyVal> keywords;

    String dctr;

    String pmzoneid;
}
