-- =================================================================================
-- PASSUNI — CHỢ ĐỒ CŨ SINH VIÊN
-- DATABASE SCHEMA (BẢN DUY NHẤT — dùng cho cả cài mới và nâng cấp)
-- Supabase PostgreSQL
--
-- Cách chạy:
--   psql "$SUPABASE_DB_URL" -f database_schema.sql
--   psql "$SUPABASE_DB_URL" -f seed_data.sql      -- (tuỳ chọn) dữ liệu mẫu
--
-- Ghi chú: file này idempotent (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS),
-- chạy lại nhiều lần không lỗi và không mất dữ liệu hiện có.
-- =================================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =================================================================================
-- 1. DANH MỤC GỐC
-- =================================================================================

CREATE TABLE IF NOT EXISTS universities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    short_name VARCHAR(100) NOT NULL,
    email_domain VARCHAR(100),
    address VARCHAR(255),
    safe_meetup_spots VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    icon VARCHAR(100),
    description VARCHAR(500)
);

-- =================================================================================
-- 2. NGƯỜI DÙNG (3 vai trò: STUDENT | MODERATOR | ADMIN)
-- =================================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    student_id VARCHAR(50),
    phone VARCHAR(30),
    avatar_url VARCHAR(1000),
    bio VARCHAR(500),
    university_id BIGINT REFERENCES universities(id) ON DELETE SET NULL,
    dorm_area VARCHAR(255),
    role VARCHAR(30) NOT NULL DEFAULT 'STUDENT',
    verified_student BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    rating DOUBLE PRECISION DEFAULT 5.0,
    completed_deals INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Ràng buộc role: STUDENT | ADMIN | MODERATOR
-- (bản schema cũ chỉ cho STUDENT/ADMIN nên phải thay thế để seed được tài khoản kiểm duyệt)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('STUDENT', 'ADMIN', 'MODERATOR'));

-- =================================================================================
-- 3. SẢN PHẨM
-- status: PENDING | APPROVED | REJECTED | IN_TRANSACTION | SOLD | HIDDEN
-- =================================================================================

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(15, 0) NOT NULL,
    original_price NUMERIC(15, 0),
    is_free BOOLEAN DEFAULT FALSE,
    is_urgent BOOLEAN DEFAULT FALSE,
    item_condition VARCHAR(50) NOT NULL DEFAULT 'LIKE_NEW_95',
    condition_note VARCHAR(255),
    pass_reason VARCHAR(255),
    pickup_spot VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(500),
    view_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    university_id BIGINT REFERENCES universities(id) ON DELETE SET NULL,
    seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chuẩn hoá tình trạng sản phẩm theo enum của backend (ProductCondition)
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_item_condition_check;
ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_item_condition;
ALTER TABLE products ADD CONSTRAINT chk_products_item_condition
    CHECK (item_condition IN ('NEW_100', 'LIKE_NEW_95', 'GOOD_85', 'USABLE'));

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_status_check;
ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_products_status;
ALTER TABLE products ADD CONSTRAINT chk_products_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'IN_TRANSACTION', 'SOLD', 'HIDDEN'));

CREATE TABLE IF NOT EXISTS product_images (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL
);

-- =================================================================================
-- 4. PHÍ SÀN THEO BẬC (admin cấu hình)
-- =================================================================================

CREATE TABLE IF NOT EXISTS platform_fee_tiers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    min_amount NUMERIC(15, 0) NOT NULL DEFAULT 0,
    max_amount NUMERIC(15, 0),                  -- NULL = không giới hạn
    fee_rate NUMERIC(5, 4) NOT NULL,            -- 0.05 = 5%
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,            -- số lớn ưu tiên trước khi khoảng trùng
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fee_tier_range CHECK (max_amount IS NULL OR max_amount >= min_amount),
    CONSTRAINT chk_fee_tier_rate CHECK (fee_rate >= 0 AND fee_rate <= 1)
);

-- =================================================================================
-- 5. ĐƠN HÀNG
-- status: PENDING | ACCEPTED | COMPLETED | DISPUTED | REJECTED | CANCELLED
-- fee_payer: SELLER | BUYER | SPLIT
-- =================================================================================

CREATE TABLE IF NOT EXISTS order_requests (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    seller_id BIGINT NOT NULL REFERENCES users(id),
    offered_price NUMERIC(15, 0) NOT NULL,
    meetup_location VARCHAR(255),
    meetup_time VARCHAR(100),
    buyer_note VARCHAR(500),
    seller_note VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    -- Phí sàn chốt tại thời điểm TẠO đơn
    platform_fee_rate NUMERIC(5, 4) DEFAULT 0,
    platform_fee_amount NUMERIC(15, 0) DEFAULT 0,
    final_seller_amount NUMERIC(15, 0),
    final_buyer_amount NUMERIC(15, 0),
    fee_payer VARCHAR(20) DEFAULT 'SELLER',
    fee_tier_id BIGINT REFERENCES platform_fee_tiers(id) ON DELETE SET NULL,
    payment_status VARCHAR(50) DEFAULT 'PENDING_PAYMENT',
    -- Vòng đời & audit
    order_code VARCHAR(20),
    confirmed_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancel_reason VARCHAR(500),
    dispute_reason VARCHAR(500),
    admin_note VARCHAR(500),
    status_updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Nâng cấp DB cũ: bổ sung cột nếu bảng đã tồn tại từ phiên bản trước
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS order_code VARCHAR(20);
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS fee_payer VARCHAR(20) DEFAULT 'SELLER';
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS fee_tier_id BIGINT REFERENCES platform_fee_tiers(id) ON DELETE SET NULL;
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS final_buyer_amount NUMERIC(15, 0);
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(500);
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS dispute_reason VARCHAR(500);
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS admin_note VARCHAR(500);
ALTER TABLE order_requests ADD COLUMN IF NOT EXISTS status_updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_code ON order_requests(order_code) WHERE order_code IS NOT NULL;

-- Sinh mã đơn cho dữ liệu cũ chưa có
UPDATE order_requests
SET order_code = 'PD' || LPAD(id::TEXT, 6, '0')
WHERE order_code IS NULL;

UPDATE order_requests SET fee_payer = 'SELLER' WHERE fee_payer IS NULL;
UPDATE order_requests SET completed_at = COALESCE(completed_at, updated_at)
WHERE status = 'COMPLETED' AND completed_at IS NULL;

-- =================================================================================
-- 6. GIAO DỊCH (chỉ sinh khi đơn COMPLETED — nguồn tính doanh thu phí sàn)
-- =================================================================================

CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES order_requests(id) ON DELETE CASCADE,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    seller_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    transaction_amount NUMERIC(15, 0) NOT NULL,
    platform_fee_rate NUMERIC(5, 4) NOT NULL,
    platform_fee_amount NUMERIC(15, 0) NOT NULL,
    seller_receive_amount NUMERIC(15, 0) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================
-- 7. CẤU HÌNH HỆ THỐNG
-- =================================================================================

CREATE TABLE IF NOT EXISTS platform_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    value VARCHAR(500) NOT NULL,
    description VARCHAR(500),
    updated_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_settings (setting_key, value, description) VALUES
('platform_fee_rate',       '0.05',   'Tỷ lệ phí sàn cơ bản (0.05 = 5%). Dùng khi đơn không khớp bậc phí nào'),
('min_platform_fee',        '2000',   'Phí sàn tối thiểu (VNĐ)'),
('max_platform_fee',        '50000',  'Phí sàn tối đa (VNĐ). 0 = không giới hạn'),
('fee_enabled',             'true',   'Bật/tắt thu phí sàn'),
('fee_payer',               'SELLER', 'Bên chịu phí: SELLER | BUYER | SPLIT'),
('fee_round_step',          '1000',   'Làm tròn phí sàn theo bước (VNĐ). 0 = không làm tròn'),
('post_require_approval',   'true',   'Bài đăng phải chờ admin duyệt trước khi hiển thị'),
('max_images_per_post',     '5',      'Số ảnh tối đa mỗi bài đăng'),
('order_auto_cancel_hours', '72',     'Tự động hủy đơn PENDING sau N giờ (0 = tắt)'),
('app_name',                'PassUni','Tên ứng dụng hiển thị')
ON CONFLICT (setting_key) DO NOTHING;

-- Bậc phí mặc định (chỉ chèn khi bảng còn rỗng)
INSERT INTO platform_fee_tiers (name, min_amount, max_amount, fee_rate, active, priority)
SELECT * FROM (VALUES
    ('Đơn nhỏ (< 100.000đ)',     0::NUMERIC,        100000::NUMERIC,  0.0300::NUMERIC, TRUE, 10),
    ('Đơn vừa (100k - 1 triệu)', 100000::NUMERIC,   1000000::NUMERIC, 0.0400::NUMERIC, TRUE, 20),
    ('Đơn lớn (> 1 triệu)',      1000000::NUMERIC,  NULL::NUMERIC,    0.0500::NUMERIC, TRUE, 30)
) AS v(name, min_amount, max_amount, fee_rate, active, priority)
WHERE NOT EXISTS (SELECT 1 FROM platform_fee_tiers);

-- =================================================================================
-- 8. TƯƠNG TÁC NGƯỜI DÙNG
-- =================================================================================

CREATE TABLE IF NOT EXISTS wishlists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, product_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    receiver_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    content VARCHAR(1000) NOT NULL,
    message_type VARCHAR(30) DEFAULT 'TEXT',   -- TEXT | OFFER | MEETUP
    offered_price NUMERIC(15, 0),
    meetup_location VARCHAR(255),
    meetup_time VARCHAR(100),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    reference_id BIGINT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES order_requests(id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    reviewed_user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id, reviewer_id)
);

CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT REFERENCES products(id) ON DELETE SET NULL,
    reason VARCHAR(100) NOT NULL,               -- FAKE_PRODUCT | SPAM | INAPPROPRIATE | FRAUD | OTHER
    detail VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | RESOLVED | DISMISSED
    admin_note VARCHAR(500),
    resolved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================
-- 9. NHẬT KÝ KIỂM DUYỆT
-- =================================================================================

CREATE TABLE IF NOT EXISTS moderation_logs (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(30) NOT NULL,                -- APPROVE | REJECT | HIDE | UNHIDE | DELETE
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =================================================================================
-- 10. INDEX
-- =================================================================================

CREATE INDEX IF NOT EXISTS idx_products_status      ON products(status);
CREATE INDEX IF NOT EXISTS idx_products_seller      ON products(seller_id);
CREATE INDEX IF NOT EXISTS idx_products_category    ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_created     ON products(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_buyer         ON order_requests(buyer_id);
CREATE INDEX IF NOT EXISTS idx_orders_seller        ON order_requests(seller_id);
CREATE INDEX IF NOT EXISTS idx_orders_status        ON order_requests(status);
CREATE INDEX IF NOT EXISTS idx_orders_product       ON order_requests(product_id);
CREATE INDEX IF NOT EXISTS idx_orders_created       ON order_requests(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_payment       ON order_requests(payment_status);

CREATE INDEX IF NOT EXISTS idx_transactions_seller  ON transactions(seller_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user   ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_reports_status       ON reports(status);
CREATE INDEX IF NOT EXISTS idx_chat_sender          ON chat_messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_chat_receiver        ON chat_messages(receiver_id);
CREATE INDEX IF NOT EXISTS idx_fee_tiers_active     ON platform_fee_tiers(active, priority DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_product   ON moderation_logs(product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_admin     ON moderation_logs(admin_id, created_at DESC);

-- =================================================================================
-- 11. VIEW BÁO CÁO DOANH THU PHÍ SÀN (theo tháng)
-- =================================================================================

CREATE OR REPLACE VIEW v_platform_revenue AS
SELECT
    DATE_TRUNC('month', t.created_at)          AS month,
    COUNT(*)                                   AS completed_orders,
    SUM(t.transaction_amount)                  AS gmv,
    SUM(t.platform_fee_amount)                 AS platform_revenue,
    SUM(t.seller_receive_amount)               AS seller_payout,
    ROUND(AVG(t.platform_fee_rate) * 100, 2)   AS avg_fee_percent
FROM transactions t
GROUP BY DATE_TRUNC('month', t.created_at)
ORDER BY month DESC;
