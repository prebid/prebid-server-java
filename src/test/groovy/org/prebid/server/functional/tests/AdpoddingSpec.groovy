package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.IgnoreRest

import java.time.Instant

import static org.prebid.server.functional.model.config.BidValidationEnforcement.ENFORCE
import static org.prebid.server.functional.model.config.BidValidationEnforcement.SKIP
import static org.prebid.server.functional.model.config.BidValidationEnforcement.WARN
import static org.prebid.server.functional.model.response.auction.ErrorType.GENERIC

class AdpoddingSpec extends BaseSpec {

    private final PrebidServerService prebidServerWithEnforceValidation = pbsServiceFactory.getService(["auction.validations.ad-podding": ENFORCE.value])
    private final PrebidServerService prebidServerWithWarnValidation = pbsServiceFactory.getService(["auction.validations.ad-podding": WARN.value])
    private final PrebidServerService prebidServerWithSkipValidation = pbsServiceFactory.getService(["auction.validations.ad-podding": SKIP.value])

    //TODO note what about metrics
    def "PBS should emit error and logs when imp.video contains rqddurs and min or max duration"() {
        given: "Test start time"
        def startTime = Instant.now()

        when: "Send auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to imp[0].video.minduration and maxduration validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        def reason = "Invalid request format: request.imp[0].video.minduration and maxduration " +
                "must not be specified while rqddurs contains at least one element"
        assert exception.responseBody == reason

        and: "Logs should be incremented"
        def logs = defaultPbsService.getLogsByTime(startTime)
        assert logs.find(it -> it.contains(reason))

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = [PBSUtils.randomNumber]
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                }
        ]
    }

    def "PBS shouldn't emit error and logs when imp.video doesn't contain rqddurs and min or max duration"() {
        when: "Send auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequest(bidResponse.id)

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = null
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = null
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        rqddurs = null
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                }
        ]
    }

    //TODO note what about metrics
    def "PBS should emit error and logs when imp.video maxseq and mincpmpersec specified without poddur"() {
        given: "Test start time"
        def startTime = Instant.now()

        when: "Send auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to imp[0] validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        def reason = "Invalid request format: request.imp[0].video.maxseq and mincpmpersec must not be " +
                "specified when poddur is not specified"
        assert exception.responseBody == reason

        and: "PBs should emit log"
        def logs = defaultPbsService.getLogsByTime(startTime)
        assert logs.find(it -> it.contains(reason))

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        maxseq = PBSUtils.randomNumber
                        poddur = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        poddur = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        maxseq = PBSUtils.randomNumber
                        poddur = null
                    }
                }
        ]
    }

    def "PBS shouldn't emit error and logs when imp.video maxseq and mincpmpersec specified with poddur"() {
        when: "Send auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequest(bidResponse.id)

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        maxseq = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        maxseq = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                }
        ]
    }

    /* def "PBS should emit error and logs when "() {
         given: "Test start time"
         def startTime = Instant.now()

         when: "Send auction request"
         defaultPbsService.sendAuctionRequest(bidRequest)

         then: "PBs should throw error due to imp[0].video.minduration and maxduration validation"
         def exception = thrown(PrebidServerException)
         assert exception.statusCode == 400
         def reason = "Invalid request format: request.imp[0].video.minduration and maxduration " +
                 "must not be specified while rqddurs contains at least one element"
         assert exception.responseBody == reason

         and: "Logs should be incremented"
         def logs = defaultPbsService.getLogsByTime(startTime)
         assert logs.find(it -> it.contains(reason))

         where:
         bidRequest << [
                 BidRequest.defaultBidRequest.tap {
                     imp[0].video = Video.defaultVideo.tap {
                         rqddurs = [PBSUtils.randomNumber]
                         maxduration = PBSUtils.randomNumber
                     }
                 },
                 BidRequest.defaultBidRequest.tap {
                     imp[0].video = Video.defaultVideo.tap {
                         rqddurs = [PBSUtils.randomNumber]
                         minduration = PBSUtils.randomNumber
                     }
                 },
                 BidRequest.defaultBidRequest.tap {
                     imp[0].video = Video.defaultVideo.tap {
                         rqddurs = [PBSUtils.randomNumber]
                         minduration = PBSUtils.randomNumber
                         maxduration = PBSUtils.randomNumber
                     }
                 }
         ]
     }
     */

    @IgnoreRest
    def "PBS should discard bid when imp.{video/audio}.podid specified and bid.dur negative or zero with ENFORCE validation"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].dur = PBSUtils.randomNegativeNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerWithEnforceValidation.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerWithEnforceValidation.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerWithEnforceValidation.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                    }
                }
        ]
    }
}
