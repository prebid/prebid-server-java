package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class DataObject {

    Integer type;

    Integer len;

    ObjectNode ext;
}
