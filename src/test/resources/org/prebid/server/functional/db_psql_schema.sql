CREATE TABLE accounts_account
(
    id                   SERIAL PRIMARY KEY,
    uuid                 varchar(40) NOT NULL,
    price_granularity    varchar(6),
    banner_cache_ttl     integer,
    video_cache_ttl      integer,
    events_enabled       boolean,
    tcf_config           text,
    truncate_target_attr smallint,
    default_integration  varchar(64),
    analytics_config     varchar(512),
    bid_validations      text,
    status               text,
    config               text,
    updated_by           integer,
    updated_by_user      varchar(64),
    updated              timestamp
);

CREATE TABLE stored_requests
(
    id          SERIAL PRIMARY KEY,
    accountId   varchar(40),
    reqId       varchar(40),
    requestData text
);

CREATE TABLE stored_imps
(
    id        SERIAL PRIMARY KEY,
    accountId varchar(40),
    impId     varchar(40),
    impData   text
);

CREATE TABLE stored_responses
(
    id                    SERIAL PRIMARY KEY,
    resId                 varchar(40) NOT NULL,
    storedAuctionResponse varchar(1024),
    storedBidResponse     varchar(1024)
);

-- set session wait timeout to 1 minute
set statement_timeout to 60000;
commit;
