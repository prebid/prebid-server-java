package org.rtb.vexing.adapter.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class IndexParams {

    @JsonProperty("siteID")
    Integer siteId;
}
