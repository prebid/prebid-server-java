package com.iab.openrtb.response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TitleObject {

    String text;

    Integer len;

    ObjectNode ext;
}
