package org.rtb.vexing.model.openrtb.ext.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext.debug
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor(staticName = "of")
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class ExtResponseDebug {

    /**
     * Defines the contract for bidresponse.ext.debug.httpcalls
     */
    Map<String, List<ExtHttpCall>> httpcalls;
}
