package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class DataObject {

    Integer type;

    Integer len;

    String value;

    ObjectNode ext;
}
