package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class TitleObject {
    Integer len;

    ObjectNode ext;
}
