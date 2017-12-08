package org.rtb.vexing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class Uids {

    @JsonProperty("uids")
    Map<String, String> uidsLegacy;

    @JsonProperty("tempUIDs")
    Map<String, UidWithExpiry> uids; // transition to new UIDs format

    Boolean optout;

    String bday;
}
