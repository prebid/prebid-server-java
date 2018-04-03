package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ImageObject {
    Integer type;

    Integer w;

    Integer wmin;

    Integer h;

    Integer hmin;

    List<String> mimes;

    ObjectNode ext;
}
