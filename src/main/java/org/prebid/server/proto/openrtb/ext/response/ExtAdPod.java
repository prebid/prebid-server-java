package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtAdPod {

    Integer podid;

    List<ExtResponseVideoTargeting> targeting;

    List<String> errors;
}
