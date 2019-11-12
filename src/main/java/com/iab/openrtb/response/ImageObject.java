package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ImageObject {

    Integer type;

    String url;

    Integer w;

    Integer h;

    ObjectNode ext;
}
