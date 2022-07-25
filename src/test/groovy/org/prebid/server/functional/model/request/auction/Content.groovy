package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

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
    Integer cattax
    List<String> cat
    Integer prodq
    Integer context
    String contentrating
    String userrating
    Integer qagmediarating
    String keywords
    List<String> kwarray
    Integer livestream
    Integer sourcerelationship
    Integer len
    String language
    String langb
    Integer embeddable
    List<Data> data
    Network network
    Channel channel

    static Content getDefaultContent() {
        new Content().tap {
            id = PBSUtils.randomString
        }
    }

    @ToString(includeNames = true, ignoreNulls = true)
    static class Channel {

        String id
        String name
        String domain

        static Channel getDefaultChannel() {
            new Channel().tap {
                id = PBSUtils.randomString
            }
        }
    }
}
