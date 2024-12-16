package org.prebid.server.proto.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.igi
 */
@Value
@Builder
public class ExtIgi {

    String impid;

    List<ExtIgiIgb> igb;

    List<ExtIgiIgs> igs;
}
