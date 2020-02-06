package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtAdPod {

    Integer podid;

    List<ExtResponseVideoTargeting> targeting;

    List<String> errors;
}
