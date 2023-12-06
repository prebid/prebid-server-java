package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.SecBrowsingTopic
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.ExtData
import org.prebid.server.functional.model.request.auction.Segment
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils

class TopicsHeaderSpec extends BaseSpec {

    private static final DEFAULT_SEGTAX_VALUE = 599

    def "PBS should populate user.data when Sec-Browsing-Topics header present in request"() {
        given: "Pbs config"
        def topicDomain = PBSUtils.randomString
        def pbsService = pbsServiceFactory.getService(["auction.privacysandbox.topicsdomain": topicDomain])

        and: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def taxonomyVersion = PBSUtils.getRandomNumber(1, 10)
        def modelVersion = PBSUtils.randomNumber
        def oneSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber.toString()], modelVersion)
        def twoSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber.toString()], modelVersion)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): "${oneSecBrowsingTopic.getValidAsHeader()} ${twoSecBrowsingTopic.getValidAsHeader()}".toString()]

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert bidderRequest.user.data[0].name == topicDomain
        assert bidderRequest.user.data[0].ext.segtax == DEFAULT_SEGTAX_VALUE + oneSecBrowsingTopic.taxonomyVersion
        assert bidderRequest.user.data[0].ext.segclass == oneSecBrowsingTopic.modelVersion
        assert bidderRequest.user.data[0].segment.id.sort().containsAll(oneSecBrowsingTopic.segments)

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
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

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
                                    SecBrowsingTopic.defaultSetBrowsingTopic(null, ["-10", "-11"]),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(1, 10), [null, null]),
                                    SecBrowsingTopic.defaultSetBrowsingTopic(PBSUtils.getRandomNumber(1, 10), [PBSUtils.randomNumber.toString()], null)];
    }

    def "PBS should populate headers with Observe-Browsing-Topics and emit warning when unknown Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): invalidSecBrowsingTopic]

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

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
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

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
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request shouldn't contain user.data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user

        and: "Bid response should contain warning"
        assert response.responseBody.contains("Invalid field in Sec-Browsing-Topics header: ${header.replace(", ", "")} discarded due to limit reached.")

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should update user.data when Sec-Browsing-Topics header present in request"() {
        given: "Pbs config"
        def topicDomain = PBSUtils.randomString
        def pbsService = pbsServiceFactory.getService(["auction.privacysandbox.topicsdomain": topicDomain])

        and: "Default basic BidRequest with generic bidder"
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
        def modelVersion = PBSUtils.randomNumber
        def secBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(taxonomyVersion, [PBSUtils.randomNumber.toString()], modelVersion)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): secBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 2
        assert bidderRequest.user.data.name.containsAll([topicDomain, bidRequest.user.data[0].name])
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
        def requestSegment = PBSUtils.getRandomNumber().toString()
        def modelVersion = PBSUtils.getRandomNumber()
        def segmentId = PBSUtils.getRandomNumber().toString()

        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User().tap {
                data = [new Data().tap {
                    name = "my_domain"
                    ext = new ExtData().tap {
                        segtax = 600
                        segclass = modelVersion
                    }
                }]
            }
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def oneSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(
                1,
                [requestSegment],
                modelVersion)
        def headerSegmentId = PBSUtils.getRandomNumber().toString()
        def twoSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(
                1,
                [headerSegmentId],
                modelVersion)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): oneSecBrowsingTopic.getValidAsHeader() +
                twoSecBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 1
        assert bidderRequest.user.data.segment.id.containsAll([segmentId, requestSegment, headerSegmentId])

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }

    def "PBS should multiple taxonomies when Sec-Browsing-Topics header present in request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = new User()
        }

        and: "Prepare valid Sec-Browsing-Topic value header"
        def oneSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(
                1,
                [PBSUtils.getRandomNumber().toString()],
                PBSUtils.randomNumber)
        def twoSecBrowsingTopic = SecBrowsingTopic.defaultSetBrowsingTopic(
                2,
                [PBSUtils.getRandomNumber().toString()],
                PBSUtils.randomNumber)
        def headers = [(HttpUtil.SEC_BROWSING_TOPICS_HEADER): oneSecBrowsingTopic.getValidAsHeader() +
                twoSecBrowsingTopic.getValidAsHeader()]

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRaw(bidRequest, headers)

        then: "Bidder request should contain user.data from header"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.data.size() == 2
        assert bidderRequest.user.data.segment.collectMany { it -> it.id }.containsAll([oneSecBrowsingTopic.segments[0], twoSecBrowsingTopic.segments[0]].sort())

        and: "Response should contain Observe-Browsing-Topics header"
        assert response.headers["Observe-Browsing-Topics"] == "?1"
    }
}
