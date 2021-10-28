package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Audio {

    List<String> mimes
    Integer minduration
    Integer maxduration
    List<Integer> protocols
    Integer startdelay
    Integer sequence
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
}
