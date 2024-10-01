package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class Eid {

    String source;

    List<Uid> uids;

    String inserter;

    String matcher;

    Integer mm;

    ObjectNode ext;
}
