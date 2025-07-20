CREATE DATABASE IF NOT EXISTS promo_dev_db;

CREATE USER IF NOT EXISTS 'promo_user'@'%' IDENTIFIED BY 'promo_pass';

GRANT ALL PRIVILEGES ON promo_dev_db.* TO 'promo_user'@'%';

FLUSH PRIVILEGES