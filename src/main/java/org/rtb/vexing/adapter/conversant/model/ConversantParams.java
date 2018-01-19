package org.rtb.vexing.adapter.conversant.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ConversantParams {

    String siteId;

    Integer secure;

    String tagId;

    Integer position;

    Float bidfloor;

    Integer mobile;

    List<String> mimes;

    List<Integer> api;

    List<Integer> protocols;

    Integer maxduration;
}
