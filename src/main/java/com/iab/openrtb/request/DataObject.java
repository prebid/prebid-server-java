package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder(toBuilder = true)
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class DataObject {

    Integer type;

    Integer len;

    ObjectNode ext;
}
