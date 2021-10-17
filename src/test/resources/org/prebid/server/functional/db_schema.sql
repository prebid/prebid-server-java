CREATE TABLE accounts_account
(
    id                        SERIAL PRIMARY KEY,
    uuid                      varchar(40) NOT NULL,
    price_granularity         varchar(6),
    banner_cache_ttl          INT,
    video_cache_ttl           INT,
    events_enabled            BIT,
    tcf_config                json,
    truncate_target_attr      TINYINT UNSIGNED,
    default_integration       varchar(64),
    analytics_config          varchar(512),
    bid_validations           json,
    status                    enum ('active','inactive'),
    config                    json,
    updated_by                int(11),
    updated_by_user           varchar(64),
    updated                   timestamp
);
CREATE TABLE s2sconfig_config
(
    id     SERIAL PRIMARY KEY,
    uuid   varchar(40) NOT NULL,
    config varchar(512)
);


CREATE TABLE stored_requests
(
    id          SERIAL PRIMARY KEY,
    accountId   varchar(40),
    reqid       varchar(40),
    requestData json
);

CREATE TABLE stored_imps
(
    id     SERIAL PRIMARY KEY,
    uuid   varchar(40) NOT NULL,
    config varchar(1024)
);
CREATE TABLE stored_responses
(
    id     SERIAL PRIMARY KEY,
    uuid   varchar(40) NOT NULL,
    config varchar(1024)
);
