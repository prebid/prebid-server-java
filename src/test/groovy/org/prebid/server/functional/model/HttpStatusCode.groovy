package org.prebid.server.functional.model

enum HttpStatusCode {

    PROCESSING_102(102),
    OK_200(200),
    NO_CONTENT_204(204),
    BAD_REQUEST_400(400),
    NOT_FOUNT_404(404),
    INTERNAL_SERVER_ERROR_500(500),
    SERVICE_UNAVAILABLE_503(503)

    Integer code

    HttpStatusCode(Integer code){
        this.code = code
    }
}
