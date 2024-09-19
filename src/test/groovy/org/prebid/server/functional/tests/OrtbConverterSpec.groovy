package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.Audio
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Dooh
import org.prebid.server.functional.model.request.auction.DoohExt
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Network
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Producer
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Qty
import org.prebid.server.functional.model.request.auction.RefSettings
import org.prebid.server.functional.model.request.auction.RefType
import org.prebid.server.functional.model.request.auction.Refresh
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.RegsExt
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

    def "PBS shouldn't move regs.{gdpr,usPrivacy} to regs.ext.{gdpr,usPrivacy} when adapter support ortb 2.6"() {
        given: "Default bid request with regs object"
        def usPrivacyRandomString = PBSUtils.randomString
        def gdpr = 0
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(usPrivacy: usPrivacyRandomString, gdpr: gdpr)
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same regs.{gdpr,usPrivacy} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.usPrivacy == usPrivacyRandomString
            regs.gdpr == gdpr
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

        then: "Bidder request should contain the same imp.rwdd as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp.first().rwdd == rwdRandomNumber
            !imp.first().ext.prebid
        }
    }

    def "PBS shouldn't move user.eids to user.ext.eids when adapter support ortb 2.6"() {
        given: "Default bid request with user.eids"
        def defaultEids = [Eid.defaultEid]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain user.eids as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.eids == defaultEids
            !user.ext
        }
    }

    def "PBS shouldn't move consent to user.ext.consent when adapter support ortb 2.6"() {
        given: "Default bid request with user.consent"
        def consentRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                consent = consentRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same user.consent as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.consent == consentRandomString
            !user.ext
        }
    }

    def "PBS shouldn't move source.schain to source.ext.schain when adapter support ortb 2.6"() {
        given: "Default bid request with source.schain"
        def defaultSource = Source.defaultSource
        def defaultSupplyChain = defaultSource.schain
        def bidRequest = BidRequest.defaultBidRequest.tap {
            source = defaultSource
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same source.schain as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            source.schain == defaultSupplyChain
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

        then: "Bidder request should contain the same source.schain as on request but should be in source.ext.schain"
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

    def "PBS should move consent to user.ext.consent when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with user.consent"
        def consentRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                consent = consentRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same user.consent as on request but should be in user.ext"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.ext.consent == consentRandomString
            !user.consent
        }
    }

    def "PBS should move eids to o user.ext.eids when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with user.eids"
        def defaultEids = [Eid.defaultEid]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                eids = defaultEids
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same user.eids as on request but should be in user.ext.eids"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            user.ext.eids.first().source == defaultEids.first().source
            user.ext.eids.first().uids.first().id == defaultEids.first().uids.first().id
            user.ext.eids.first().uids.first().atype == defaultEids.first().uids.first().atype
            !user.eids
        }
    }

    def "PBS should move regs to regs.ext.{gdpr,upPrivacy} when adapter doesn't support ortb 2.6"() {
        given: "Default bid request with regs object"
        def usPrivacyRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                usPrivacy = usPrivacyRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same regs object as on request but should be in regs.ext"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.ext.usPrivacy == usPrivacyRandomString
            regs.ext.gdpr == 0
            !regs.usPrivacy
            !regs.gdpr
        }
    }

    def "PBS should copy rewarded video to imp.ext.prebid.isRewardedInventory when adapter support ortb 2.6"() {
        given: "Default bid request with rwdd"
        def rwdRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].rwdd = rwdRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same imp.rwdd as on request but should be also in ext.prebid"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].ext.prebid.isRewardedInventory == rwdRandomNumber
            imp[0].rwdd == rwdRandomNumber
        }
    }

    def "PBS shouldn't remove wlangb when bidder supports ortb 2.6"() {
        given: "Default bid request with wlangb"
        def wlangbRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            wlangb = wlangbRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn contain the wlangb as on request"
        assert bidder.getBidderRequest(bidRequest.id).wlangb == wlangbRandomStrings
    }

    def "PBS shouldn't remove wlangb when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with wlangb"
        def wlangbRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            wlangb = wlangbRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the wlangb as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            wlangb == wlangbRandomStrings
        }
    }

    def "PBS shouldn't remove device.langb when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with device.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the device.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).device.langb == langbRandomString
    }

    def "PBS shouldn't remove device.langb when bidder supports ortb 2.6"() {
        given: "Default bid request with device.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the device.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).device.langb == langbRandomString
    }

    def "PBS shouldn't remove site.content.langb when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.langb == langbRandomString
    }

    def "PBS shouldn't remove site.content.langb when bidder supports ortb 2.6"() {
        given: "Default bid request with site.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.langb == langbRandomString
    }

    def "PBS shouldn't remove app.content.langb when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with app.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.content.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.content.langb == langbRandomString
    }

    def "PBS shouldn't remove app.content.langb when bidder supports ortb 2.6"() {
        given: "Default bid request with app.content.langb"
        def langbRandomString = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                langb = langbRandomString
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.content.langb as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.content.langb == langbRandomString
    }

    def "PBS shouldn't remove site.publisher.cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.publisher.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.publisher.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.publisher.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.publisher.cattax when bidder supports ortb 2.6"() {
        given: "Default bid request with site.publisher.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.publisher = Publisher.defaultPublisher.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.publisher.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.publisher.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.content.producer.cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.content.producer.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                producer = Producer.defaultProducer.tap {
                    cattax = cattaxRandomNumber
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.producer.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.producer.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.content.producer.cattax when bidder supports ortb 2.6"() {
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

        then: "Bidder request should contain the site.content.producer.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.producer.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove app.cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with app.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.catTax == cattaxRandomNumber
    }

    def "PBS shouldn't remove app.cattax when bidder supports ortb 2.6"() {
        given: "Default bid request with app.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.catTax == cattaxRandomNumber
    }

    def "PBS shouldn't remove cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cattax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove cattax when bidder supports ortb 2.6"() {
        given: "Default bid request with cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            cattax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.catTax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.cattax when bidder supports ortb 2.6"() {
        given: "Default bid request with site.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.catTax = cattaxRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.catTax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.content.cattax when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.content.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove site.content.cattax when bidder supports ortb 2.5"() {
        given: "Default bid request with site.content.cattax"
        def cattaxRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                cattax = cattaxRandomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.cattax as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.cattax == cattaxRandomNumber
    }

    def "PBS shouldn't remove imp[0].video.* and keep imp[0].video.plcmt when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with imp[0].video.*"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.defaultVideo.tap {
                rqddurs = [PBSUtils.randomNumber]
                maxseq = PBSUtils.randomNumber
                poddur = PBSUtils.randomNumber
                podid = PBSUtils.randomNumber
                podseq = PBSUtils.randomNumber
                mincpmpersec = PBSUtils.randomDecimal
                slotinpod = PBSUtils.randomNumber
                plcmt = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
                podDeduplication = [PBSUtils.randomNumber]
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].video.* as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].video == bidRequest.imp[0].video
    }

    def "PBS shouldn't remove imp[0].video.* when bidder supports ortb 2.6"() {
        given: "Default bid request with imp[0].video.*"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.defaultVideo.tap {
                rqddurs = [PBSUtils.randomNumber]
                maxseq = PBSUtils.randomNumber
                poddur = PBSUtils.randomNumber
                podid = PBSUtils.randomNumber
                podseq = PBSUtils.randomNumber
                mincpmpersec = PBSUtils.randomDecimal
                slotinpod = PBSUtils.randomNumber
                plcmt = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
                podDeduplication = [PBSUtils.randomNumber, PBSUtils.randomNumber]
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].video.* as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].video == bidRequest.imp[0].video
    }

    def "PBS shouldn't remove imp[0].audio.* when bidder doesn't support ortb 2.6"() {
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

        then: "Bidder request should contain the imp[0].audio.* as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].audio == bidRequest.imp[0].audio
    }

    def "PBS shouldn't remove imp[0].audio.* when bidder supports ortb 2.6"() {
        given: "Default bid request with imp[0].audio.*"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].audio = Audio.defaultAudio.tap {
                rqddurs = [PBSUtils.randomNumber]
                maxseq = PBSUtils.randomNumber
                poddur = PBSUtils.randomNumber
                podid = PBSUtils.randomNumber
                podseq = PBSUtils.randomNumber
                mincpmpersec = BigDecimal.valueOf(1)
                slotinpod = PBSUtils.randomNumber
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].audio.* as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].audio == bidRequest.imp[0].audio
    }

    def "PBS shouldn't remove imp[0].ssai when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with imp[0].ssai"
        def randomSsai = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ssai = randomSsai
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].ssai as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ssai == randomSsai
    }

    def "PBS shouldn't remove imp[0].ssai when bidder supports ortb 2.6"() {
        given: "Default bid request with imp[0].ssai"
        def ssaiRandomNumber = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ssai = ssaiRandomNumber
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].ssai as on request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ssai == ssaiRandomNumber
    }

    def "PBS shouldn't remove site.content.{channel, network} when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.content.{network, channel}"
        def defaultChannel = Channel.defaultChannel
        def defaultNetwork = Network.defaultNetwork
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                it.channel = defaultChannel
                it.network = defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.{network, channel} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.channel.id == defaultChannel.id
            site.content.network.id == defaultNetwork.id
        }
    }

    def "PBS shouldn't remove site.content.{channel, network} when bidder supports ortb 2.6"() {
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

        then: "Bidder request should contain the site.content.{channel, network} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            site.content.channel.id == defaultChannel.id
            site.content.network.id == defaultNetwork.id
        }
    }

    def "PBS shouldn't remove app.content.{channel, network} when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with app.content.{network, channel}"
        def defaultChannel = Channel.defaultChannel
        def defaultNetwork = Network.defaultNetwork
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.content = Content.defaultContent.tap {
                channel = defaultChannel
                network = defaultNetwork
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.content.{network, channel} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.content.channel.id == defaultChannel.id
            app.content.network.id == defaultNetwork.id
        }
    }

    def "PBS shouldn't remove app.content.{channel, network} when bidder supports ortb 2.6"() {
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

        then: "Bidder response should contain the app.content.{channel, network} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            app.content.channel.id == defaultChannel.id
            app.content.network.id == defaultNetwork.id
        }
    }

    def "PBS shouldn't remove site.kwarray when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.kwarray"
        def randomKwArray = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.kwArray = randomKwArray
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.kwArray == randomKwArray
    }

    def "PBS shouldn't remove site.kwarray when bidder supports ortb 2.6"() {
        given: "Default bid request with site.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.kwArray = kwarrayRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.kwArray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove site.content.kwarray when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with site.content.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.kwarray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove site.content.kwarray when bidder supports ortb 2.6"() {
        given: "Default bid request with site.content.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.content = Content.defaultContent.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the site.content.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.content.kwarray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove app.kwarray when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with app.kwarray"
        def randomKwArray = [PBSUtils.randomString]
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.kwArray = randomKwArray
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.kwArray == randomKwArray
    }

    def "PBS shouldn't remove app.kwarray when bidder supports ortb 2.6"() {
        given: "Default bid request with app.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.kwArray = kwarrayRandomStrings
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.kwArray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove user.kwarray when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with user.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't contain the user.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).user.kwarray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove user.kwarray when bidder supports ortb 2.6"() {
        given: "Default bid request with user.kwarray"
        def kwarrayRandomStrings = [PBSUtils.randomString]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            user = User.defaultUser.tap {
                kwarray = kwarrayRandomStrings
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the user.kwarray as on request"
        assert bidder.getBidderRequest(bidRequest.id).user.kwarray == kwarrayRandomStrings
    }

    def "PBS shouldn't remove device.sua when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def model = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                sua = new UserAgent().tap {
                    it.model = model
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the device.sua as on request"
        assert bidder.getBidderRequest(bidRequest.id).device.sua.model == model
    }

    def "PBS shouldn't remove device.sua when bidder supports ortb 2.6"() {
        given: "Default bid request with device.sua"
        def model = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            device = new Device().tap {
                sua = new UserAgent().tap {
                    it.model = model
                }
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the device.sua as on request"
        assert bidder.getBidderRequest(bidRequest.id).device.sua.model == model
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
        def catRandomNumber = [PBSUtils.randomString]
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid.first().bid.first().langb = langbRandomString
            seatbid.first().bid.first().dur = durRandomNumber
            seatbid.first().bid.first().apis = apisRandomNumbers
            seatbid.first().bid.first().slotinpod = slotinpodRandomNumber
            seatbid.first().bid.first().cattax = cattaxRandomNumber
            seatbid.first().bid.first().cat = catRandomNumber
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction with ortb 2.5"
        def response = prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the lang, dur, apis, slotinpod, cattax,cat as on request"
        verifyAll(response) {
            seatbid.first().bid.first().langb == langbRandomString
            seatbid.first().bid.first().dur == durRandomNumber
            seatbid.first().bid.first().apis == apisRandomNumbers
            seatbid.first().bid.first().slotinpod == slotinpodRandomNumber
            seatbid.first().bid.first().cattax == cattaxRandomNumber
            seatbid.first().bid.first().cat == catRandomNumber
        }
    }

    def "PBS shouldn't remove gpp and gppSid when PBS don't support ortb 2.6"() {
        given: "Default bid request with device.sua"
        def randomGpp = PBSUtils.randomString
        def randomGppSid = [PBSUtils.getRandomNumber(), PBSUtils.getRandomNumber()]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = new Regs(gpp: randomGpp, gppSid: randomGppSid)
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the regs.gpp and regs.gppSid as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            regs.gpp == bidRequest.regs.gpp
            regs.gppSid.eachWithIndex { value, i -> bidRequest.regs.gppSid[i] == value }
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
            regs.gppSid.eachWithIndex { value, i -> bidRequest.regs.gppSid[i] == value }
        }
    }

    def "PBS shouldn't remove imp[0].{refresh/qty/dt} when bidder doesn't support ortb 2.6"() {
        given: "Default bid request with imp[0].{refresh/qty/dt}"
        def refresh = new Refresh(count: PBSUtils.randomNumber, refSettings:
                [new RefSettings(refType: PBSUtils.getRandomEnum(RefType), minInt: PBSUtils.randomNumber)])
        def qty = new Qty(multiplier: PBSUtils.randomDecimal, sourceType: PBSUtils.getRandomEnum(SourceType),
                vendor: PBSUtils.randomString)
        def dt = PBSUtils.randomDecimal
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.refresh = refresh
                it.qty = qty
                it.dt = dt
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].{refresh/qty/dt} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].refresh == refresh
            imp[0].qty == qty
            imp[0].dt == dt
        }
    }

    def "PBS shouldn't remove imp[0].{refresh/qty/dt} when bidder supports ortb 2.6"() {
        given: "Default bid request with imp[0].{refresh/qty/dt}"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.refresh = new Refresh(count: PBSUtils.randomNumber, refSettings:
                        [new RefSettings(refType: PBSUtils.getRandomEnum(RefType), minInt: PBSUtils.randomNumber)])
                it.qty = new Qty(multiplier: PBSUtils.randomDecimal,
                        sourceType: PBSUtils.getRandomEnum(SourceType),
                        vendor: PBSUtils.randomString)
                it.dt = PBSUtils.randomDecimal
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the imp[0].{refresh/qty/dt} as on request"
        verifyAll(bidder.getBidderRequest(bidRequest.id)) {
            imp[0].refresh == bidRequest.imp[0].refresh
            imp[0].qty == bidRequest.imp[0].qty
            imp[0].dt == bidRequest.imp[0].dt
        }
    }

    def "PBS shouldn't remove site.inventoryPartnerDomain when PBS don't support ortb 2.6"() {
        given: "Default bid request with site.inventoryPartnerDomain"
        def inventoryPartnerDomain = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.inventoryPartnerDomain = inventoryPartnerDomain
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the app.inventoryPartnerDomain as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.inventoryPartnerDomain == inventoryPartnerDomain
    }

    def "PBS shouldn't remove site.inventoryPartnerDomain when PBS support ortb 2.6"() {
        given: "Default bid request with site.inventoryPartnerDomain"
        def inventoryPartnerDomain = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            site.inventoryPartnerDomain = inventoryPartnerDomain
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "BidderRequest should contain the site.inventoryPartnerDomain as on request"
        assert bidder.getBidderRequest(bidRequest.id).site.inventoryPartnerDomain == inventoryPartnerDomain
    }

    def "PBS shouldn't remove app.inventoryPartnerDomain when PBS don't support ortb 2.6"() {
        given: "Default bid request with app.inventoryPartnerDomain"
        def inventoryPartnerDomain = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.inventoryPartnerDomain = inventoryPartnerDomain
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.inventoryPartnerDomain as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.inventoryPartnerDomain == inventoryPartnerDomain
    }

    def "PBS shouldn't remove app.inventoryPartnerDomain when PBS support ortb 2.6"() {
        given: "Default bid request with app.inventoryPartnerDomain"
        def inventoryPartnerDomain = PBSUtils.randomString
        def bidRequest = BidRequest.getDefaultBidRequest(APP).tap {
            app.inventoryPartnerDomain = inventoryPartnerDomain
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the app.inventoryPartnerDomain as on request"
        assert bidder.getBidderRequest(bidRequest.id).app.inventoryPartnerDomain == inventoryPartnerDomain
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

        then: "Bidder request should contain the bidRequest.dooh as on request"
        assert bidder.getBidderRequest(bidRequest.id).dooh == bidRequest.dooh
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

        then: "Bidder request should contain the bidRequest.dooh as on request"
        assert bidder.getBidderRequest(bidRequest.id).dooh == bidRequest.dooh
    }

    def "PBS shouldn't remove regs.ext.gpc when ortb request support ortb 2.6"() {
        given: "Default bid request with regs.ext object"
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                ext = new RegsExt(gpc: randomGpc)
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same regs as on request"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.gpc == randomGpc
    }

    def "PBS shouldn't remove regs.ext.gpc when ortb request doesn't support ortb 2.6"() {
        given: "Default bid request with regs.ext object"
        def randomGpc = PBSUtils.randomNumber as String
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs = Regs.defaultRegs.tap {
                ext = new RegsExt(gpc: randomGpc)
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain the same regs as on request"
        assert bidder.getBidderRequest(bidRequest.id).regs.ext.gpc == randomGpc
    }

    def "PBS shouldn't remove video.protocols when ortb request support 2.6"() {
        given: "Default bid request with Banner object"
        def protocols = [PBSUtils.randomNumber]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.getDefaultVideo().tap {
                it.protocols = protocols
            }
        }

        when: "Requesting PBS auction with ortb 2.6"
        prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain video.protocols on request"
        assert bidder.getBidderRequest(bidRequest.id).imp[0].video.protocols == protocols
    }

    def "PBS shouldn't remove video.protocols when ortb request support 2.5"() {
        given: "Default bid request with Banner object"
        def protocols = [PBSUtils.randomNumber]
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].video = Video.getDefaultVideo().tap {
                it.protocols = protocols
            }
        }

        when: "Requesting PBS auction with ortb 2.5"
        prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain video.protocols on request"
        assert bidder.getBidderRequest(bidRequest.id).imp[0].video.protocols == protocols
    }

    def "PBS shouldn't remove saetbid[0].bid[].{lang,dur.slotinpod,apis,cat,cattax} when ortb request support 2.5"() {
        given: "Default bid request with stored request object"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response"
        def langb = PBSUtils.randomString
        def dur = PBSUtils.randomNumber
        def slotinpod = PBSUtils.randomNumber
        def apis = [PBSUtils.randomNumber]
        def cat = [PBSUtils.randomString]
        def cattax = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].tap {
                it.langb = langb
                it.dur = dur
                it.slotinpod = slotinpod
                it.apis = apis
                it.cat = cat
                it.cattax = cattax
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction with ortb 2.5"
        def response = prebidServerServiceWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain seat[0].bid[0].{langb,dur,slotinpod,apis,cattax,cat} on request"
        verifyAll(response.seatbid[0].bid[0]) {
            it.langb == langb
            it.dur == dur
            it.slotinpod == slotinpod
            it.apis == apis
            it.cattax == cattax
            it.cat == cat
        }
    }

    def "PBS shouldn't remove saetbid[0].bid[].{lang,dur.slotinpod,apis,cat,cattax} when ortb request support 2.6"() {
        given: "Default bid request with stored request object"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(bidRequest)
        storedRequestDao.save(storedRequest)

        and: "Default bidder response "
        def langb = PBSUtils.randomString
        def dur = PBSUtils.randomNumber
        def slotinpod = PBSUtils.randomNumber
        def apis = [PBSUtils.randomNumber]
        def cat = [PBSUtils.randomString]
        def cattax = PBSUtils.randomNumber
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].tap {
                it.langb = langb
                it.dur = dur
                it.slotinpod = slotinpod
                it.apis = apis
                it.cattax = cattax
                it.cat = cat
            }
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Requesting PBS auction with ortb 2.6"
        def response = prebidServerServiceWithNewOrtb.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain seat[0].bid[0].{langb,dur,slotinpod,apis,cattax,cat} on request"
        verifyAll(response.seatbid[0].bid[0]) {
            it.langb == langb
            it.dur == dur
            it.slotinpod == slotinpod
            it.apis == apis
            it.cattax == cattax
            it.cat == cat
        }
    }
}
