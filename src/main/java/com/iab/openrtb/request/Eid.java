package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

@Value
@Builder(toBuilder = true)
@NonFinal
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Eid {

    String source;

    List<Uid> uids;

    String inserter;

    String matcher;

    Integer mm;

    ObjectNode ext;
}
