package org.prebid.server.functional.tests.storage

import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.testcontainers.PbsServiceFactory
import org.prebid.server.functional.tests.BaseSpec
import org.prebid.server.functional.util.PBSUtils

class StorageBaseSpec extends BaseSpec {

    protected static final String INVALID_FILE_BODY = 'INVALID'
    protected static final def DEFAULT_BUCKET = PBSUtils.randomString.toLowerCase()

    protected static final S3Service S3_SERVICE = new S3Service(DEFAULT_BUCKET)

    protected static Map<String, String> s3StorageConfig = [
            'settings.s3.accessKeyId'         : "${S3_SERVICE.accessKeyId}".toString(),
            'settings.s3.secretAccessKey'     : "${S3_SERVICE.secretKeyId}".toString(),
            'settings.s3.endpoint'            : "${S3_SERVICE.endpoint}".toString(),
            'settings.s3.bucket'              : "${DEFAULT_BUCKET}".toString(),
            'settings.s3.accounts-dir'        : "${S3Service.DEFAULT_ACCOUNT_DIR}".toString(),
            'settings.s3.stored-imps-dir'     : "${S3Service.DEFAULT_IMPS_DIR}".toString(),
            'settings.s3.stored-requests-dir' : "${S3Service.DEFAULT_REQUEST_DIR}".toString(),
            'settings.s3.stored-responses-dir': "${S3Service.DEFAULT_RESPONSE_DIR}".toString(),
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
