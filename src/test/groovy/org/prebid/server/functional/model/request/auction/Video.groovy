package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Video {

    List<String> mimes
    Integer minduration
    Integer maxduration
    Integer startdelay
    Integer maxseq
    Integer poddur
    List<Integer> protocols
    @JsonProperty("w")
    Integer weight
    @JsonProperty("h")
    Integer height
    Integer podid
    Integer podseq
    @JsonProperty("rqddurs")
    List<Integer> requireExactDuration
    VideoPlacementSubtypes placement
    VideoPlcmtSubtype plcmt
    Integer linearity
    Integer skip
    Integer skipmin
    Integer skipafter
    Integer sequence
    Integer slotinpod
    BigDecimal mincpmpersec
    List<Integer> battr
    Integer maxextended
    Integer minbitrate
    Integer maxbitrate
    Integer boxingallowed
    List<Integer> playbackmethod
    Integer playbackend
    List<Integer> delivery
    Integer pos
    List<Banner> companionad
    List<Integer> api
    List<Integer> companiontype
    @JsonProperty("poddedupe")
    List<Integer> podDeduplication

    static Video getDefaultVideo() {
        new Video(mimes: ["video/mp4"], weight: 300, height: 200)
    }
}
