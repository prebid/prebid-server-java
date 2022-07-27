package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Audio {

    List<String> mimes
    Integer minduration
    Integer maxduration
    Integer poddur
    List<Integer> protocols
    Integer startdelay
    List<Integer> rqddurs
    Integer podid
    Integer podseq
    Integer sequence
    Integer slotinpod
    BigDecimal mincpmpersec
    List<Integer> battr
    Integer maxextended
    Integer minbitrate
    Integer maxbitrate
    List<Integer> delivery
    List<Banner> companionad
    List<Integer> api
    List<Integer> companiontype
    Integer maxseq
    Integer feed
    Integer stitched
    Integer nvol

    static Audio getDefaultAudio() {
        new Audio().tap {
            mimes = ["audio/mp4"]
        }
    }
}
