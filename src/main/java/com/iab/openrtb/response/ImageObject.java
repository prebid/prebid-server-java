package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageObject {

    Integer type;

    String url;

    Integer w;

    Integer h;

    ObjectNode ext;
}
