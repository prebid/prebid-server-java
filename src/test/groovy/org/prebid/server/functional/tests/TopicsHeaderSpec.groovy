package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.SecBrowsingTopic
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.ExtData
import org.prebid.server.functional.model.request.auction.Segment
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

class TopicsHeaderSpec extends BaseSpec {

    private static final DEFAULT_SEGTAX_VALUE = 599
    private static final PRIVACY_SENDBOX_DOMAIN = "privacy-sandbox-domain"

    private final PrebidServerService prebidServerServiceWithTopicsDomain
            = pbsServiceFactory.getService(["auction.privacysandbox.topicsdomain": PRIVACY_SENDBOX_DOMAIN])

    def "PBS should populate user.data when Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def taxonomyVersion = PBSUtils.getRandomNumber(1, 10)
        def modelVersion = PBSUtils.randomString
        def firstSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber as String], modelVersion)
        def secondSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber as String], modelVersion)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): "${firstSecBrowsingTopic.getValidAsHeader()} ${secondSecBrowsingTopic.getValidAsHeader()}".toString()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert bidderRequest.user.data[0].name == PRIVACY_SENDBOX_DOMAIN
        assert bidderRequest.user.data[0].ext.segtax == DEFAULT_SEGTAX_VALUE + firstSecBrowsingTopic.taxonomyVersion
        assert bidderRequest.user.data[0].ext.segclass == firstSecBrowsingTopic.modelVersion
        assert bidderRequest.user.data[0].segment.id.sort().containsAll(firstSecBrowsingTopic.segments)

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should populate headers with Observe-Browsing-Topics and emit warning when Sec-Browsing-Topics invalid header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): invalidSecBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request shouldn't contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.responseBody.contains("\"warnings\":{\"prebid\":[{\"code\":999,\"message\":\"Invalid field " +
                "in Sec-Browsing-Topics header: (${invalidSecBrowsingTopic.segments.join(' ')});v=chrome.1:${invalidSecBrowsingTopic.taxonomyVersion}:${invalidSecBrowsingTopic.modelVersion}\"}]},\"")

        where:
        invalidSecBrowsingTopic << [SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(11)),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(1, 10), ["-10", "-11"]),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(null),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(1, 10), [Long.MAX_VALUE as String]),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(1, 10), [null, null])]
    }

    def "PBS should populate headers with Observe-Browsing-Topics and emit warning when unknown Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): invalidSecBrowsingTopic]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request shouldn't contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.responseBody.contains("\"warnings\":{\"prebid\":[{\"code\":999,\"message\":\"Invalid field " +
                "in Sec-Browsing-Topics header: $invalidSecBrowsingTopic")

        where:
        invalidSecBrowsingTopic << [PBSUtils.randomString,
                                    SecBrowsingTopic.defaultSetBrowsingTopic().getInvalidAsHeader()]
    }

    def "PBS should populate max 10 user.data when header Sec-Browsing-Topics contain more then 10 in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Create Sec-Browsing-Topics 12 headers"
        def stringBuilder = new StringBuilder()
        (1..11).each { taxonomyVersion ->
            stringBuilder.append(SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion).getValidAsHeader())
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): stringBuilder.toString()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain 10 user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 10

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS shouldn't populate user.data when header Sec-Browsing-Topics contain 10 `p=` value and 11 valid"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Create Sec-Browsing-Topics 12 headers"
        def stringBuilder = new StringBuilder("();p=P000000000," * 11)

        and: "Prepare valid Sec-Browsing-Topic value header"
        def header = SecBrowsingTopic.defaultSetBrowsingTopic().getValidAsHeader()
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): stringBuilder.toString() + header]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request shouldn't contain user.data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user

        and: "Bid response should contain warning"
        assert response.responseBody.contains("Invalid field in Sec-Browsing-Topics header: ${header.replace(", ", "")} discarded due to limit reached.")

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should update user.data when Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User().tap {
                data = [new Data().tap {
                    name = PBSUtils.randomString
                    segment = [new Segment(id: PBSUtils.randomNumber.toString())]
                    ext = new ExtData().tap {
                        segtax = PBSUtils.randomNumber
                        segclass = PBSUtils.randomNumber
                    }
                }]
            }
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def taxonomyVersion = PBSUtils.getRandomNumber(1, 10)
        def modelVersion = PBSUtils.randomString
        def secBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber as String], modelVersion)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): secBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 2
        assert bidderRequest.user.data.name.containsAll([PRIVACY_SENDBOX_DOMAIN, bidRequest.user.data[0].name])
        assert bidderRequest.user.data.ext.segtax.containsAll([DEFAULT_SEGTAX_VALUE + secBrowsingTopic.taxonomyVersion,
                                                               bidRequest.user.data[0].ext.segtax])
        assert bidderRequest.user.data.ext.segclass.containsAll(secBrowsingTopic.modelVersion, bidRequest.user.data[0].ext.segclass)
        assert bidderRequest.user.data.segment.id.sort().containsAll([bidRequest.user.data[0].segment.id,
                                                                      secBrowsingTopic.segments].sort())

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should overlap segments when Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def randomSegClass = PBSUtils.randomNumber as String
        def randomSegment = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest().tap {
            user = new User().tap {
                data = [new Data().tap {
                    name = PRIVACY_SENDBOX_DOMAIN
                    segment = [new Segment(id: randomSegment)]
                    ext = new ExtData().tap {
                        segtax = 600
                        segclass = randomSegClass
                    }
                }]
            }
        }

        and: "Prepare browsing topics header headers"
        def oneSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(1, [PBSUtils.randomNumber as String], randomSegClass)
        def secondSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(1, [PBSUtils.randomNumber as String], randomSegClass)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): oneSecBrowsingTopic.getValidAsHeader() +
                secondSecBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert bidderRequest.user.data[0].segment.id ==
                [randomSegment as String, oneSecBrowsingTopic.segments[0], secondSecBrowsingTopic.segments[0]]

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should multiple taxonomies when Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def oneSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(1)
        def twoSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(2)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): oneSecBrowsingTopic.getValidAsHeader() +
                twoSecBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 2
        assert bidderRequest.user.data.name == [PRIVACY_SENDBOX_DOMAIN, PRIVACY_SENDBOX_DOMAIN]
        assert bidderRequest.user.data.segment.collectMany { it -> it.id }
                .containsAll([oneSecBrowsingTopic.segments[0], twoSecBrowsingTopic.segments[0]])

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should populate user.data with empty name when privacy sand box present with empty name"() {
        given: "PBS with empty auction privacy sand box"
        def prebidServerServiceWithTopicsDomain
                = pbsServiceFactory.getService(["auction.privacysandbox.topicsdomain": topicsdomain])

        and: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def secBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic()
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): secBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = prebidServerServiceWithTopicsDomain.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header and name with empty string"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert bidderRequest.user.data[0].name == topicsdomain
        assert bidderRequest.user.data[0].segment.id.sort() == secBrowsingTopic.segments.sort()

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"

        where:
        topicsdomain << [null, ""]
    }
}
