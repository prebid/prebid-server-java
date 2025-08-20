package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.response.auction.BidExt
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.Meta
import org.prebid.server.functional.model.response.auction.Prebid
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

    /*  private static String ORTB_2_6_PROPERTY_VERSION = ["adapters.generic.ortb-version": "2.6"]
      private static String ORTB_2_5_PROPERTY_VERSION = ["adapters.generic.ortb-version": "2.5"]
      private static String ENABLED_AD_POD_PROPERTY_SUPPORT = ["adapters.generic.adpod-supported": "true"]
      private static String DISABLED_AD_POD_PROPERTY_SUPPORT = ["adapters.generic.adpod-supported": "false"]
  */
    private final PrebidServerService prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod = pbsServiceFactory.getService(["auction.validations.ad-podding"  : ENFORCE.value,
                                                                                                                                    "adapters.generic.ortb-version"   : "2.6",
                                                                                                                                    "adapters.generic.adpod-supported": true as String])
    private final PrebidServerService prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod = pbsServiceFactory.getService(["auction.validations.ad-podding"  : WARN.value,
                                                                                                                                 "adapters.generic.ortb-version"   : "2.6",
                                                                                                                                 "adapters.generic.adpod-supported": true as String])
    private final PrebidServerService prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod = pbsServiceFactory.getService(["auction.validations.ad-podding"  : SKIP.value,
                                                                                                                                 "adapters.generic.ortb-version"   : "2.6",
                                                                                                                                 "adapters.generic.adpod-supported": true as String])
    private final static Integer ZERO = 0

    //TODO: NOTE should emit metrics
    def "PBS should emit error and logs when imp.video.rqddurs and min or max duration specified"() {
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
                        requireExactDuration = [PBSUtils.randomNumber]
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        requireExactDuration = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        requireExactDuration = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                }
        ]
    }

    def "PBS should emit error and logs when imp.audio.rqddurs and min or max duration specified"() {
        given: "Test start time"
        def startTime = Instant.now()

        when: "Send auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to imp[0].audio.minduration and maxduration validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        def reason = "Invalid request format: request.imp[0].audio.minduration and maxduration " +
                "must not be specified while rqddurs contains at least one element"
        assert exception.responseBody == reason

        and: "Logs should be incremented"
        def logs = defaultPbsService.getLogsByTime(startTime)
        assert logs.find(it -> it.contains(reason))

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = [PBSUtils.randomNumber]
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = [PBSUtils.randomNumber]
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                }
        ]
    }

    def "PBS shouldn't emit error and logs when imp.{video/audio} doesn't contain rqddurs and min or max duration"() {
        when: "Send auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequest(bidResponse.id)

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        requireExactDuration = null
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        requireExactDuration = null
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].video = Video.defaultVideo.tap {
                        requireExactDuration = null
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = null
                        maxduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = null
                        minduration = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        requireExactDuration = null
                        minduration = PBSUtils.randomNumber
                        maxduration = PBSUtils.randomNumber
                    }
                },
        ]
    }

    def "PBS should emit error and logs when imp.video.maxseq or mincpmpersec specified without poddur"() {
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

    def "PBS should emit error and logs when imp.audio.maxseq or mincpmpersec specified without poddur"() {
        given: "Test start time"
        def startTime = Instant.now()

        when: "Send auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should throw error due to imp validation"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        def reason = "Invalid request format: request.imp[0].audio.maxseq and mincpmpersec must not be specified when poddur is not specified"
        assert exception.responseBody == reason

        and: "PBs should emit log"
        def logs = defaultPbsService.getLogsByTime(startTime)
        assert logs.find(it -> it.contains(reason))

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        maxseq = PBSUtils.randomNumber
                        poddur = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        poddur = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        maxseq = PBSUtils.randomNumber
                        poddur = null
                    }
                }
        ]
    }

    def "PBS shouldn't emit error and logs when imp.{video/audio} maxseq and mincpmpersec specified with poddur"() {
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
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        maxseq = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.defaultAudio.tap {
                        mincpmpersec = PBSUtils.randomNumber
                        maxseq = PBSUtils.randomNumber
                        poddur = PBSUtils.randomNumber
                    }
                }
        ]
    }

    //todo ENFORCE

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid not specified"() {
        given: "Bid request without podId"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.getDefaultVideo()
            imp[0].audio = Audio.getDefaultAudio()
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid specified and doesn't {video/audio}.rqddurs"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid and {video/audio}.rqddurs specified"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur/seat.bid.ext.prebid.meta.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid not specified and bid.dur negative or zero"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNegativeNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.errors

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = null
                    }
                }
        ]
    }

    def "PBS should discard bid and populate errors by enforce validation when imp.{video/audio}.podid specified and bid.dur negative or zero"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS should discard bid and populate errors by enforce validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur negative or zero"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))

        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid specified and bid.ext.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS shouldn't discard bid by enforce validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS should discard bid by enforce validation when request imp.{video/audio}.rqddurs and seat.bid.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
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
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS should discard bid by enforce validation when request imp.{video/audio}.rqddurs and seat.bid.ext.prebid.meta.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
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
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS should discard bid by enforce validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.duration response"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid with seatBid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

    def "PBS should discard bid by enforce validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.ext.prebid.meta.duration response"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid with seatBid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.err"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.err"] == 1

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.errors[GENERIC]*.code == [5]
        assert response.ext?.errors[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

    /*def "PBS shouldn't  "() {
        given: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].dur = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(dur: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS shouldn't WHEN DUR VALID AND RQDDRS SAME VALUE"() {
        given: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].dur = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(dur: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }*/

    //todo WARN

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid not specified"() {
        given: "Bid request without podId"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.getDefaultVideo()
            imp[0].audio = Audio.getDefaultAudio()
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid specified and doesn't {video/audio}.rqddurs"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid and {video/audio}.rqddurs specified"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur/seat.bid.ext.prebid.meta.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid not specified and bid.dur negative or zero"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNegativeNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]

        and: "Response shouldn't contain warnings or error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = null
                    }
                }
        ]
    }

    def "PBS should populate warning and not discard bid by warn validation when imp.{video/audio}.podid specified and bid.dur negative or zero"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS should populate warning and not discard bid by warn validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur negative or zero"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid specified and bid.ext.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS shouldn't discard bid by warn validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS should discard bid by warn validation when request imp.{video/audio}.rqddurs and seat.bid.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
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
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS should discard bid by warn validation when request imp.{video/audio}.rqddurs and seat.bid.ext.prebid.meta.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
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
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS should discard bid by warn validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.duration response"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid with seatBid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

    def "PBS should discard bid by warn validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.ext.prebid.meta.duration response"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid with seatBid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndWarnValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert metrics["account.${bidRequest.accountId}.response.validation.pod.warn"] == 1
        assert metrics["adapter.${GENERIC.value}.response.validation.pod.warn"] == 1

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response should contain error"
        def errorMessage = "BidResponse validation `${ENFORCE.value}`: bidder `${GENERIC.value}` " +
                "response triggers ad podding validation for bid ${bidResponse.seatbid[0].bid[0].id}, " +
                "account=${bidRequest.accountId}, referrer=${bidRequest.site.page}"
        assert response.ext?.warnings[GENERIC]*.code == [5]
        assert response.ext?.warnings[GENERIC]*.message == ["BidId `${bidResponse.seatbid[0].bid[0].id}` validation messages: Error: " + errorMessage]

        and: "PBs should emit log"
        def logsByTime = prebidServerServiceOrtb26AndEnforceValidationWithEnabledAdPod.getLogsByTime(startTime)
        def bidId = bidResponse.seatbid[0].bid[0].id
        def responseCorrection = getLogsByText(logsByTime, bidId)
        assert responseCorrection[0].contains(errorMessage)
        assert responseCorrection.size() == 1

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

    //todo 4.6.1 hb_dur For now we should skip this

    //todo: SKIP

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid not specified"() {
        given: "Bid request without podId"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.getDefaultVideo()
            imp[0].audio = Audio.getDefaultAudio()
            imp[0].nativeObj = Native.defaultNative
        }

        and: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid specified and doesn't {video/audio}.rqddurs"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = null
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid and {video/audio}.rqddurs specified"() {
        given: "Default basic bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid and {video/audio}.rqddurs and seat.bid.dur/seat.bid.ext.prebid.meta.dur specified"() {
        given: "Default basic bid response with dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should contain seatBid"
        assert response.seatbid

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].audio = Audio.getDefaultAudio()
                    imp[0].nativeObj = Native.defaultNative
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid not specified and bid.dur negative or zero"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNegativeNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]

        and: "Response shouldn't contain warnings and error and seatNonBid"
        assert !response.ext?.errors

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = null
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = null
                    }
                }
        ]
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid specified and bid.dur negative or zero"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain error"
        assert !response.ext?.errors

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur negative or zero"() {
        given: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))

        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs should emit pod err metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]

        and: "Response shouldn't contain error and warning"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        where:
        dur                           | bidRequest
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
            }
        }
        PBSUtils.randomNegativeNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
        ZERO                          | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
            }
        }
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid specified and bid.ext.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS shouldn't discard bid by skip validation when imp.{video/audio}.podid specified and bid.ext.prebid.meta.dur same sa imp.{video/audio}.rqddurs"() {
        given: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                requireExactDuration = [dur]
            }
        }
    }

    def "PBS shouldn't discard bid by skip validation when request imp.{video/audio}.rqddurs and seat.bid.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = PBSUtils.randomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS shouldn't discard bid by skip validation when request imp.{video/audio}.rqddurs and seat.bid.ext.prebid.meta.dur are not same"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default basic bid with seatbid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: PBSUtils.randomNumber)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "PBS response should contain seatBid"
        assert response.seatbid

        and: "Response shouldn't contain warnings and error"
        assert !response.ext?.warnings
        assert !response.ext?.errors

        where:
        bidRequest << [
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].video = Video.getDefaultVideo().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                },
                BidRequest.defaultBidRequest.tap {
                    imp[0].banner = null
                    imp[0].audio = Audio.getDefaultAudio().tap {
                        podid = PBSUtils.randomNumber
                        requireExactDuration = [PBSUtils.randomNumber]
                    }
                }]
    }

    def "PBS shouldn't discard bid by skip validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.duration response"() {
        given: "Default bid with seatBid[].bid[].duration"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = dur
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain error and warning"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

    def "PBS shouldn't discard bid by skip validation when request imp.{video/audio}.{max/min}duration out of bound seatBid.bid.ext.prebid.meta.duration response"() {
        given: "Default bid with seatBid[].bid[].ext.prebid.meta.dur"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].duration = null
            seatbid[0].bid[0].ext = new BidExt(prebid: new Prebid(meta: new Meta(duration: dur)))
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction"
        def response = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendAuctionRequest(bidRequest)

        then: "Pbs shouldn't emit pod err or warn metrics"
        def metrics = prebidServerServiceOrtb26AndSkipValidationWithEnabledAdPod.sendCollectedMetricsRequest()
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.err"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.err"]
        assert !metrics["account.${bidRequest.accountId}.response.validation.pod.warn"]
        assert !metrics["adapter.${GENERIC.value}.response.validation.pod.warn"]

        and: "Response shouldn't contain error and warning"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        where:
        dur                   | bidRequest
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null                //todo : WHY WITH BANNER DOESN"T WORK
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].video = Video.getDefaultVideo().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                maxduration = dur - 1
            }
        }
        PBSUtils.randomNumber | BidRequest.defaultBidRequest.tap {
            imp[0].banner = null
            imp[0].audio = Audio.getDefaultAudio().tap {
                podid = PBSUtils.randomNumber
                minduration = dur + 1
            }
        }
    }

}
