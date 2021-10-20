package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Content {

    String id
    Integer episode
    String title
    String series
    String season
    String artist
    String genre
    String album
    String isrc
    Producer producer
    String url
    List<String> cat
    Integer prodq
    Integer context
    String contentrating
    String userrating
    Integer qagmediarating
    String keywords
    Integer livestream
    Integer sourcerelationship
    Integer len
    String language
    Integer embeddable
    List<Data> data
}
