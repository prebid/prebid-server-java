package org.prebid.server.functional.tests.storage

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

class StorageBaseSpec extends BaseSpec {

    protected static final String INVALID_FILE_BODY = 'INVALID'
    protected static final String DEFAULT_BUCKET = PBSUtils.randomString.toLowerCase()

    protected static final S3Service s3Service = new S3Service(Dependencies.localStackContainer)

    def setupSpec() {
        s3Service.createBucket(DEFAULT_BUCKET)
    }

    def cleanupSpec() {
        s3Service.purgeBucketFiles(DEFAULT_BUCKET)
        s3Service.deleteBucket(DEFAULT_BUCKET)
    }

    protected static Map<String, String> s3StorageConfig = [
            'settings.s3.accessKeyId'         : s3Service.accessKeyId,
            'settings.s3.secretAccessKey'     : s3Service.secretKeyId,
            'settings.s3.endpoint'            : s3Service.endpoint,
            'settings.s3.bucket'              : DEFAULT_BUCKET,
            'settings.s3.region'              : s3Service.region,
            'settings.s3.force-path-style'    : 'true',
            'settings.s3.accounts-dir'        : S3Service.DEFAULT_ACCOUNT_DIR,
            'settings.s3.stored-imps-dir'     : S3Service.DEFAULT_IMPS_DIR,
            'settings.s3.stored-requests-dir' : S3Service.DEFAULT_REQUEST_DIR,
            'settings.s3.stored-responses-dir': S3Service.DEFAULT_RESPONSE_DIR,
    ]

    protected static Map<String, String> mySqlDisabledConfig =
            ['settings.database.type'                     : null,
             'settings.database.host'                     : null,
             'settings.database.port'                     : null,
             'settings.database.dbname'                   : null,
             'settings.database.user'                     : null,
             'settings.database.password'                 : null,
             'settings.database.pool-size'                : null,
             'settings.database.provider-class'           : null,
             'settings.database.account-query'            : null,
             'settings.database.stored-requests-query'    : null,
             'settings.database.amp-stored-requests-query': null,
             'settings.database.stored-responses-query'   : null
            ].asImmutable() as Map<String, String>

    protected PrebidServerService s3StoragePbsService = PbsServiceFactory.getService(s3StorageConfig + mySqlDisabledConfig)
}
