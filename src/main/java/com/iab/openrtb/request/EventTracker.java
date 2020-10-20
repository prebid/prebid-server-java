package com.iab.openrtb.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class EventTracker {

    Integer event;

    List<Integer> methods;

    ObjectNode ext;
}
