package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class VideoObject {

    List<String> mimes;

    Integer minduration;

    Integer maxduration;

    List<Integer> protocols;

    Integer w;

    Integer h;

    ObjectNode ext;
}
