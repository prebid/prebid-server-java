package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

@Builder(toBuilder = true)
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoObject {

    List<String> mimes;

    Integer minduration;

    Integer maxduration;

    List<Integer> protocols;

    Integer w;

    Integer h;

    ObjectNode ext;
}
