package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Dooh
import org.prebid.server.functional.model.request.auction.DoohExt
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Network
import org.prebid.server.functional.model.request.auction.Producer
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Qty
import org.prebid.server.functional.model.request.auction.RefSettings
import org.prebid.server.functional.model.request.auction.RefType
import org.prebid.server.functional.model.request.auction.Refresh
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.Source
import org.prebid.server.functional.model.request.auction.SourceType
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserAgent
import org.prebid.server.functional.model.request.auction.Video
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static org.prebid.server.functional.model.request.auction.Content.Channel
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH

class OrtbConverterSpec extends BaseSpec {

    private final static String ORTB_PROPERTY_VERSION = "adapters.generic.ortb-version"

    @Shared
    PrebidServerService prebidServerServiceWithNewOrtb = pbsServiceFactory.getService([(ORTB_PROPERTY_VERSION): "2.6"])
    @Shared
    PrebidServerService prebidServerServiceWithElderOrtb = pbsServiceFactory.getService([(ORTB_PROPERTY_VERSION): "2.5"])

    def "PBS shouldn't move regs to past location when adapter support ortb 2.6"() {
        given: "Default bid request with regs object"
        def usPrivacyRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                usPrivacy = usPrivacyRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same regs as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.usPrivacy == usPrivacyRandomString
            regs.gdpr == 0
            !regs.ext
        }
    }

    def "PBS shouldn't move rwdd to past location when adapter support ortb 2.6"() {
        given: "Default bid request with imp.rwdd"
        def rwdRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                rwdd = rwdRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same imp.rwdd as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp.first().rwdd == rwdRandomNumber
            !imp.first().ext.prebid
        }
    }

    def "PBS shouldn't move eids to past location when adapter support ortb 2.6"() {
        given: "Default bid request with user.eids"
        def defaultEids = [Eid.defaultEid]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same user.eids as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.eids.first().source == defaultEids.first().source
            user.eids.first().uids.first().id == defaultEids.first().uids.first().id
            user.eids.first().uids.first().atype == defaultEids.first().uids.first().atype
            !user.ext
        }
    }

    def "PBS shouldn't move consent to past location when adapter support ortb 2.6"() {
        given: "Default bid request with user.consent"
        def consentRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                consent = consentRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same user.consent as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.consent == consentRandomString
            !user.ext
        }
    }

    def "PBS shouldn't move schain to past location when adapter support ortb 2.6"() {
        given: "Default bid request with source.schain"
        def defaultSource = Source.defaultSource
        def defaultSupplyChain = defaultSource.schain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            source = defaultSource
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same source.schain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            source.schain.ver == defaultSupplyChain.ver
            source.schain.complete == defaultSupplyChain.complete
            source.schain.nodes.first().asi == defaultSupplyChain.nodes.first().asi
            source.schain.nodes.first().sid == defaultSupplyChain.nodes.first().sid
            source.schain.nodes.first().rid == defaultSupplyChain.nodes.first().rid
            source.schain.nodes.first().name == defaultSupplyChain.nodes.first().name
            source.schain.nodes.first().domain == defaultSupplyChain.nodes.first().domain
            source.schain.nodes.first().hp == defaultSupplyChain.nodes.first().hp
            !source.ext
        }
    }

    def "PBS should move schain to past location when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with source.schain"
        def defaultSource = Source.defaultSource
        def defaultSupplyChain = defaultSource.schain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            source = defaultSource
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same source.schain as on request but should be in source.ext.schain"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            source.ext.schain.ver == defaultSupplyChain.ver
            source.ext.schain.complete == defaultSupplyChain.complete
            source.ext.schain.nodes.first().asi == defaultSupplyChain.nodes.first().asi
            source.ext.schain.nodes.first().sid == defaultSupplyChain.nodes.first().sid
            source.ext.schain.nodes.first().rid == defaultSupplyChain.nodes.first().rid
            source.ext.schain.nodes.first().name == defaultSupplyChain.nodes.first().name
            source.ext.schain.nodes.first().domain == defaultSupplyChain.nodes.first().domain
            source.ext.schain.nodes.first().hp == defaultSupplyChain.nodes.first().hp
            !source.schain
        }
    }

    def "PBS should move consent to past location when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with user.consent"
        def consentRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                consent = consentRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same user.consent as on request but should be in user.ext"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.ext.consent == consentRandomString
            !user.consent
        }
    }

    def "PBS should move eids to past location when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with user.eids"
        def defaultEids = [Eid.defaultEid]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same user.eids as on request but should be in user.ext.eids"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.ext.eids.first().source == defaultEids.first().source
            user.ext.eids.first().uids.first().id == defaultEids.first().uids.first().id
            user.ext.eids.first().uids.first().atype == defaultEids.first().uids.first().atype
            !user.eids
        }
    }

    def "PBS should move regs to past location when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with regs object"
        def usPrivacyRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                usPrivacy = usPrivacyRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same regs object as on request but should be in regs.ext"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.ext.usPrivacy == usPrivacyRandomString
            regs.ext.gdpr == 0
            !regs.usPrivacy
            !regs.gdpr
        }
    }

    def "PBS should move rewarded video to past location when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with rwdd"
        def rwdRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                rwdd = rwdRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same imp.rwdd as on request but should be in ext.prebid"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp.first().ext.prebid.isRewardedInventory == rwdRandomNumber
            !imp.first().rwdd
        }
    }

    def "PBS shouldn't remove wlangb when we we support ortb 2.6"() {
        given: "Default bid request with wlangb"
        def wlangbRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            wlangb = wlangbRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn contain the wlangb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            wlangb == wlangbRandomStrings
        }
    }

    def "PBS should remove wlangb when we don't support ortb 2.6"() {
        given: "Default bid request with wlangb"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            wlangb = [PBSUtils.randomString]
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the wlangb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !wlangb
        }
    }

    def "PBS should remove device.langb when we don't support ortb 2.6"() {
        given: "Default bid request with device.langb"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                langb = PBSUtils.randomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the device.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !device.langb
        }
    }

    def "PBS shouldn't remove device.langb when we support ortb 2.6"() {
        given: "Default bid request with device.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the device.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            device.langb == langbRandomString
        }
    }

    def "PBS should remove site.content.langb when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.langb"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                langb = PBSUtils.randomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.content.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.content.langb
        }
    }

    def "PBS shouldn't remove site.content.langb when we support ortb 2.6"() {
        given: "Default bid request with site.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.content.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.langb == langbRandomString
        }
    }

    def "PBS should remove app.content.langb when we don't support ortb 2.6"() {
        given: "Default bid request with app.content.langb"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                langb = PBSUtils.randomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the app.content.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !app.content.langb
        }
    }

    def "PBS shouldn't remove app.content.langb when we support ortb 2.6"() {
        given: "Default bid request with app.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the app.content.langb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.content.langb == langbRandomString
        }
    }

    def "PBS should remove site.publisher.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with site.publisher.cattax"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap {
                cattax = PBSUtils.randomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.publisher.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.publisher.cattax
        }
    }

    def "PBS shouldn't remove site.publisher.cattax when we support ortb 2.6"() {
        given: "Default bid request with site.publisher.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.publisher.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.publisher.cattax == cattaxRandomNumber
        }
    }

    def "PBS should remove site.content.producer.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.producer.cattax"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                producer = Producer.defaultProducer.tap {
                    cattax = PBSUtils.randomNumber
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.content.producer.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.content.producer.cattax
        }
    }

    def "PBS shouldn't remove site.content.producer.cattax when we support ortb 2.6"() {
        given: "Default bid request with site.content.producer.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                producer = Producer.defaultProducer.tap {
                    cattax = cattaxRandomNumber
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.content.producer.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.producer.cattax == cattaxRandomNumber
        }
    }

    def "PBS should remove app.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with app.cattax"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.catTax = PBSUtils.randomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the app.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !app.catTax
        }
    }

    def "PBS shouldn't remove app.cattax when we support ortb 2.6"() {
        given: "Default bid request with app.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the app.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.catTax == cattaxRandomNumber
        }
    }

    def "PBS should remove cattax when we don't support ortb 2.6"() {
        given: "Default bid request with cattax"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cattax = PBSUtils.randomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !cattax
        }
    }

    def "PBS shouldn't remove cattax when we support ortb 2.6"() {
        given: "Default bid request with cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cattax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            cattax == cattaxRandomNumber
        }
    }

    def "PBS should remove site.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with site.cattax"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.catTax = PBSUtils.randomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.catTax
        }
    }

    def "PBS shouldn't remove site.cattax when we support ortb 2.6"() {
        given: "Default bid request with site.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.catTax == cattaxRandomNumber
        }
    }

    def "PBS should remove site.content.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.cattax"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                cattax = PBSUtils.randomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.content.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.content.cattax
        }
    }

    def "PBS shouldn't remove site.content.cattax when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.content.cattax as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.cattax == cattaxRandomNumber
        }
    }

    def "PBS should remove imp[0].video.* and keep imp[0].video.plcmt when we don't support ortb 2.6"() {
        given: "Default bid request with imp[0].video.*"
        def placement = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.defaultVideo.tap {
                rqddurs = [PBSUtils.randomNumber]
                maxseq = PBSUtils.randomNumber
                poddur = PBSUtils.randomNumber
                podid = PBSUtils.randomNumber
                podseq = PBSUtils.randomNumber
                mincpmpersec = PBSUtils.randomDecimal
                slotinpod = PBSUtils.randomNumber
                plcmt = placement
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain the imp[0].video.* as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            !imp[0].video.rqddurs
            !imp[0].video.maxseq
            !imp[0].video.poddur
            !imp[0].video.podid
            !imp[0].video.podseq
            !imp[0].video.mincpmpersec
            !imp[0].video.slotinpod
        }

        and: "Bidder request should contain the imp[0].video.* as on request"
        bidderRequest.imp[0].video.plcmt == placement
    }

    def "PBS shouldn't remove imp[0].video.* when we support ortb 2.6"() {
        given: "Default bid request with imp[0].video.*"
        def rqddursListOfRandomNumber = [PBSUtils.randomNumber]
        def maxseqRandomNumber = PBSUtils.randomNumber
        def poddurRandomNumber = PBSUtils.randomNumber
        def podidRandomNumber = PBSUtils.randomNumber
        def podseqRandomNumber = PBSUtils.randomNumber
        def mincpmpersecRandomNumber = PBSUtils.randomDecimal
        def slotinpodRandomNumber = PBSUtils.randomNumber
        def plcmtRandomEnum = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.defaultVideo.tap {
                rqddurs = rqddursListOfRandomNumber
                maxseq = maxseqRandomNumber
                poddur = poddurRandomNumber
                podid = podidRandomNumber
                podseq = podseqRandomNumber
                mincpmpersec = mincpmpersecRandomNumber
                slotinpod = slotinpodRandomNumber
                plcmt = plcmtRandomEnum
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the imp[0].video.* as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].video.rqddurs == rqddursListOfRandomNumber
            imp[0].video.maxseq == maxseqRandomNumber
            imp[0].video.poddur == poddurRandomNumber
            imp[0].video.podid == podidRandomNumber
            imp[0].video.podseq == podseqRandomNumber
            imp[0].video.mincpmpersec == mincpmpersecRandomNumber
            imp[0].video.slotinpod == slotinpodRandomNumber
            imp[0].video.plcmt == plcmtRandomEnum
        }
    }

    def "PBS should remove imp[0].audio.* when we don't support ortb 2.6"() {
        given: "Default bid request with imp[0].audio.*"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].audio = Audio.defaultAudio.tap {
                rqddurs = [PBSUtils.randomNumber]
                maxseq = PBSUtils.randomNumber
                poddur = PBSUtils.randomNumber
                podid = PBSUtils.randomNumber
                podseq = PBSUtils.randomNumber
                mincpmpersec = PBSUtils.randomDecimal
                slotinpod = PBSUtils.randomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the imp[0].audio.* as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !imp[0].audio.rqddurs
            !imp[0].audio.maxseq
            !imp[0].audio.poddur
            !imp[0].audio.podid
            !imp[0].audio.podseq
            !imp[0].audio.mincpmpersec
            !imp[0].audio.slotinpod
        }
    }

    def "PBS shouldn't remove imp[0].audio.* when we support ortb 2.6"() {
        given: "Default bid request with imp[0].audio.*"
        def rqddursListOfRandomNumber = [PBSUtils.randomNumber]
        def maxseqRandomNumber = PBSUtils.randomNumber
        def poddurRandomNumber = PBSUtils.randomNumber
        def podidRandomNumber = PBSUtils.randomNumber
        def podseqRandomNumber = PBSUtils.randomNumber
        def mincpmpersecRandomNumber = PBSUtils.randomDecimal
        def slotinpodRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].audio = Audio.defaultAudio.tap {
                rqddurs = rqddursListOfRandomNumber
                maxseq = maxseqRandomNumber
                poddur = poddurRandomNumber
                podid = podidRandomNumber
                podseq = podseqRandomNumber
                mincpmpersec = mincpmpersecRandomNumber
                slotinpod = slotinpodRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the imp[0].audio.* as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].audio.rqddurs == rqddursListOfRandomNumber
            imp[0].audio.maxseq == maxseqRandomNumber
            imp[0].audio.poddur == poddurRandomNumber
            imp[0].audio.podid == podidRandomNumber
            imp[0].audio.podseq == podseqRandomNumber
            imp[0].audio.mincpmpersec == mincpmpersecRandomNumber
            imp[0].audio.slotinpod == slotinpodRandomNumber
        }
    }

    def "PBS should remove imp[0].ssai when we don't support ortb 2.6"() {
        given: "Default bid request with imp[0].ssai"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ssai = PBSUtils.randomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the imp[0].ssai as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !imp[0].ssai
        }
    }

    def "PBS shouldn't remove imp[0].ssai when we support ortb 2.6"() {
        given: "Default bid request with imp[0].ssai"
        def ssaiRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ssai = ssaiRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the imp[0].ssai as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].ssai == ssaiRandomNumber
        }
    }

    def "PBS should remove site.content.{channel, network} when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.{network, channel}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                channel = Channel.defaultChannel
                network = Network.defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.content.{network, channel} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.content.channel
            !site.content.network
        }
    }

    def "PBS shouldn't remove site.content.{channel, network} when we support ortb 2.6"() {
        given: "Default bid request with site.content.{network, channel}"
        def defaultChannel = Channel.defaultChannel
        def defaultNetwork = Network.defaultNetwork
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                channel = defaultChannel
                network = defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.content.{channel, network} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.channel.id == defaultChannel.id
            site.content.network.id == defaultNetwork.id
        }
    }

    def "PBS should remove app.content.{channel, network} when we don't support ortb 2.6"() {
        given: "Default bid request with app.content.{network, channel}"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                channel = Channel.defaultChannel
                network = Network.defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the app.content.{network, channel} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !app.content.channel
            !app.content.network
        }
    }

    def "PBS shouldn't remove app.content.{channel, network} when we support ortb 2.6"() {
        given: "Default bid request with content.{network, channel}"
        def defaultChannel = Channel.defaultChannel
        def defaultNetwork = Network.defaultNetwork
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                channel = defaultChannel
                network = defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the app.content.{channel, network} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.content.channel.id == defaultChannel.id
            app.content.network.id == defaultNetwork.id
        }
    }

    def "PBS should remove site.kwarray when we don't support ortb 2.6"() {
        given: "Default bid request with site.kwarray"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.kwArray = [PBSUtils.randomString]
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.kwArray
        }
    }

    def "PBS shouldn't remove site.kwarray when we support ortb 2.6"() {
        given: "Default bid request with site.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.kwArray = kwarrayRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.kwArray == kwarrayRandomStrings
        }
    }

    def "PBS should remove site.content.kwarray when we don't support ortb 2.6"() {
        given: "Default bid request with site.content.kwarray"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                kwarray = [PBSUtils.randomString]
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the site.content.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.content.kwarray
        }
    }

    def "PBS shouldn't remove site.content.kwarray when we support ortb 2.6"() {
        given: "Default bid request with site.content.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the site.content.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.kwarray == kwarrayRandomStrings
        }
    }

    def "PBS should remove app.kwarray when we don't support ortb 2.6"() {
        given: "Default bid request with app.kwarray"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.kwArray = [PBSUtils.randomString]
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the app.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !app.kwArray
        }
    }

    def "PBS shouldn't remove app.kwarray when we support ortb 2.6"() {
        given: "Default bid request with app.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.kwArray = kwarrayRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the app.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.kwArray == kwarrayRandomStrings
        }
    }

    def "PBS should remove user.kwarray when we don't support ortb 2.6"() {
        given: "Default bid request with user.kwarray"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                kwarray = [PBSUtils.randomString]
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the user.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !user.kwarray
        }
    }

    def "PBS shouldn't remove user.kwarray when we support ortb 2.6"() {
        given: "Default bid request with user.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the user.kwarray as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.kwarray == kwarrayRandomStrings
        }
    }

    def "PBS should remove device.sua when we don't support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                sua = new UserAgent().tap {
                    model = PBSUtils.randomString
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the device.sua as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !device.sua
        }
    }

    def "PBS shouldn't remove device.sua when we support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def modelRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                sua = new UserAgent().tap {
                    model = modelRandomString
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the device.sua as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            device.sua.model == modelRandomString
        }
    }

    def "PBS should pass bid[].{langb, dur, slotinpor, apis, cattax} through to response"() {
        given: "Default bid request with defaultBidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default basic bid with lang, dur, apis, slotinpod, cattax"
        def langbRandomString = PBSUtils.randomString
        def durRandomNumber = PBSUtils.randomNumber
        def apisRandomNumbers = [PBSUtils.randomNumber]
        def slotinpodRandomNumber = PBSUtils.randomNumber
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().langb = langbRandomString
            seatbid.first().bid.first().dur = durRandomNumber
            seatbid.first().bid.first().apis = apisRandomNumbers
            seatbid.first().bid.first().slotinpod = slotinpodRandomNumber
            seatbid.first().bid.first().cattax = cattaxRandomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction with ortb 2.5"
        def response = prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the lang, dur, apis, slotinpod, cattax as on request"
        verifyAll(response) {
            seatbid.first().bid.first().langb == langbRandomString
            seatbid.first().bid.first().dur == durRandomNumber
            seatbid.first().bid.first().apis == apisRandomNumbers
            seatbid.first().bid.first().slotinpod == slotinpodRandomNumber
            seatbid.first().bid.first().cattax == cattaxRandomNumber
        }
    }

    def "PBS should remove gpp and gppSid when PBS don't support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: PBSUtils.randomString, gppSid: [PBSUtils.getRandomNumber(),
                                                                 PBSUtils.getRandomNumber()])
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't contain the regs.gpp and regs.gppSid as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !regs.gpp
            !regs.gppSid
        }
    }

    def "PBS shouldn't remove gpp and gppSid when PBS support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: PBSUtils.randomString, gppSid: [PBSUtils.getRandomNumber(),
                                                                 PBSUtils.getRandomNumber()])
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the regs.gpp and regs.gppSid as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.gpp == bidRequest.regs.gpp
            regs.gppSid.eachWithIndex { Integer value, int i -> bidRequest.regs.gppSid[i] == value }
        }
    }

    def "PBS should remove imp[0].{refresh/qty/dt} when we don't support ortb 2.6"() {
        given: "Default bid request with imp[0].{refresh/qty/dt}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                refresh = new Refresh(count: PBSUtils.randomNumber, refSettings: [new RefSettings(
                        refType: PBSUtils.getRandomEnum(RefType),
                        minInt: PBSUtils.randomNumber)])
                qty = new Qty(multiplier: PBSUtils.randomDecimal,
                        sourceType: PBSUtils.getRandomEnum(SourceType),
                        vendor: PBSUtils.randomString)
                dt = PBSUtils.randomDecimal
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse shouldn't contain the imp[0].{refresh/qty/dt} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !imp[0].refresh
            !imp[0].qty
            !imp[0].dt
        }
    }

    def "PBS shouldn't remove imp[0].{refresh/qty/dt} when we support ortb 2.6"() {
        given: "Default bid request with imp[0].{refresh/qty/dt}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                refresh = new Refresh(count: PBSUtils.randomNumber, refSettings: [new RefSettings(
                        refType: PBSUtils.getRandomEnum(RefType),
                        minInt: PBSUtils.randomNumber)])
                qty = new Qty(multiplier: PBSUtils.randomDecimal,
                        sourceType: PBSUtils.getRandomEnum(SourceType),
                        vendor: PBSUtils.randomString)
                dt = PBSUtils.randomDecimal
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the imp[0].{refresh/qty/dt} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].refresh.count == bidRequest.imp[0].refresh.count
            imp[0].refresh.refSettings[0].refType == bidRequest.imp[0].refresh.refSettings[0].refType
            imp[0].refresh.refSettings[0].minInt == bidRequest.imp[0].refresh.refSettings[0].minInt
            imp[0].qty.multiplier == bidRequest.imp[0].qty.multiplier
            imp[0].qty.sourceType == bidRequest.imp[0].qty.sourceType
            imp[0].qty.vendor == bidRequest.imp[0].qty.vendor
            imp[0].dt == bidRequest.imp[0].dt
        }
    }

    def "PBS should remove site.inventoryPartnerDomain when PBS don't support ortb 2.6"() {
        given: "Default bid request with site.inventoryPartnerDomain"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.inventoryPartnerDomain = PBSUtils.randomString
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't contain the app.inventoryPartnerDomain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !site.inventoryPartnerDomain
        }
    }

    def "PBS shouldn't remove site.inventoryPartnerDomain when PBS support ortb 2.6"() {
        given: "Default bid request with site.inventoryPartnerDomain"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.inventoryPartnerDomain = PBSUtils.randomString
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the site.inventoryPartnerDomain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.inventoryPartnerDomain == bidRequest.site.inventoryPartnerDomain
        }
    }

    def "PBS should remove app.inventoryPartnerDomain when PBS don't support ortb 2.6"() {
        given: "Default bid request with app.inventoryPartnerDomain"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.inventoryPartnerDomain = PBSUtils.randomString
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't contain the app.inventoryPartnerDomain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !app.inventoryPartnerDomain
        }
    }

    def "PBS shouldn't remove app.inventoryPartnerDomain when PBS support ortb 2.6"() {
        given: "Default bid request with app.inventoryPartnerDomain"
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.inventoryPartnerDomain = PBSUtils.randomString
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the app.inventoryPartnerDomain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.inventoryPartnerDomain == bidRequest.app.inventoryPartnerDomain
        }
    }

    def "PBS should remove bidRequest.dooh when PBS don't support ortb 2.6"() {
        given: "Default bid request with bidRequest.dooh"
        def bidRequest = BidRequest.getDefaultBidRequest(DOOH).tap {
            dooh = new Dooh().tap {
                id = PBSUtils.randomString
                name = PBSUtils.randomString
                venueType = [PBSUtils.randomString]
                venueTypeTax = PBSUtils.randomNumber
                publisher = Publisher.defaultPublisher
                domain = PBSUtils.randomString
                keywords = PBSUtils.randomString
                content = Content.defaultContent
                ext = DoohExt.defaultDoohExt
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest shouldn't contain the bidRequest.dooh as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            !dooh
        }
    }

    def "PBS shouldn't remove bidRequest.dooh when PBS support ortb 2.6"() {
        given: "Default bid request with bidRequest.dooh"
        def bidRequest = BidRequest.getDefaultBidRequest(DOOH).tap {
            dooh = new Dooh().tap {
                id = PBSUtils.randomString
                name = PBSUtils.randomString
                venueType = [PBSUtils.randomString]
                venueTypeTax = PBSUtils.randomNumber
                publisher = Publisher.defaultPublisher
                domain = PBSUtils.randomString
                keywords = PBSUtils.randomString
                content = Content.defaultContent
                ext = DoohExt.defaultDoohExt
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the bidRequest.dooh as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            dooh.id == bidRequest.dooh.id
            dooh.name == bidRequest.dooh.name
            dooh.venueType == bidRequest.dooh.venueType
            dooh.venueTypeTax == bidRequest.dooh.venueTypeTax
            dooh.publisher.id == bidRequest.dooh.publisher.id
            dooh.domain == bidRequest.dooh.domain
            dooh.keywords == bidRequest.dooh.keywords
            dooh.content.id == bidRequest.dooh.content.id
            dooh.ext.data == bidRequest.dooh.ext.data
        }
    }

    def "PBS shouldn't remove regs.ext.gpc when ortb request support ortb 2.6"() {
        given: "Default bid request with regs.ext object"
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                ext.gpc = randomGpc
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same regs as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.ext.gpc == randomGpc
        }
    }

    def "PBS shouldn't remove regs.ext.gpc when ortb request doesn't support ortb 2.6"() {
        given: "Default bid request with regs.ext object"
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                ext.gpc = randomGpc
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidResponse should contain the same regs as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.ext.gpc == randomGpc
        }
    }
}
