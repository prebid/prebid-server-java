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
public class ImageObject {

    Integer type;

    Integer w;

    Integer wmin;

    Integer h;

    Integer hmin;

    List<String> mimes;

    ObjectNode ext;
}
