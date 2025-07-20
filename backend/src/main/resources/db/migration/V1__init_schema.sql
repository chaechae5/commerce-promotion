create table IF NOT EXISTS coupons
(
    id             bigint auto_increment
        primary key,
    public_id      varchar(21)                       not null,
    code           varchar(255)                       not null,
    name           varchar(255)                       null,
    description    varchar(255)                       null,
    total_quantity int      default 0                 not null,
    start_date     datetime                           null,
    end_date       datetime                           null,
    expire_date    datetime                           null,
    valid_days     int                                null,
    created_at     datetime default CURRENT_TIMESTAMP null,
    updated_at     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uq_public_id
        unique (public_id),
    constraint uq_code
        unique (code)
);

create table IF NOT EXISTS coupons
(
    id         bigint auto_increment
        primary key,
    coupon_id  bigint                                not null,
    user_id    varchar(255)                          not null,
    issued_at  datetime    default CURRENT_TIMESTAMP null,
    expire_at  datetime                              null,
    status     varchar(20) default 'ISSUED'          null,
    used_date  datetime                              null,
    created_at datetime    default CURRENT_TIMESTAMP null,
    updated_at datetime    default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    public_id  varchar(36)                           not null,
    constraint uq_coupon_user
        unique (coupon_id, user_id),
    constraint fk_coupon
        foreign key (coupon_id) references promo_db.coupons (id)
);

