package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Video {

    List<String> mimes
    Integer minduration
    Integer maxduration
    List<Integer> protocols
    Integer w
    Integer h
    Integer startdelay
    Integer placement
    Integer linearity
    Integer skip
    Integer skipmin
    Integer skipafter
    Integer sequence
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

    static Video getDefaultVideo() {
            new Video(mimes: ["video/mp4"], w: 300, h: 200)
    }
}
