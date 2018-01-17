package org.rtb.vexing.adapter.pubmatic.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class PubmaticParams {

    String publisherId;

    String adSlot;
}
