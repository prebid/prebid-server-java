package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class TitleObject {

    String text;

    Integer len;

    ObjectNode ext;
}
