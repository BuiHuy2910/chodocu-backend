-- =================================================================================
-- SCRIPT CƠ SỞ DỮ LIỆU: PASSUNI - CHỢ ĐỒ CŨ SINH VIÊN
-- Hỗ trợ: Supabase (PostgreSQL) & MySQL
-- Project Supabase: https://ovxnlepmewindufcgxln.supabase.co
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

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    student_id VARCHAR(50),
    phone VARCHAR(30),
    avatar_url VARCHAR(1000),
    university_id BIGINT REFERENCES universities(id),
    dorm_area VARCHAR(255),
    role VARCHAR(30) NOT NULL DEFAULT 'STUDENT',
    verified_student BOOLEAN DEFAULT TRUE,
    active BOOLEAN DEFAULT TRUE,
    rating DOUBLE PRECISION DEFAULT 5.0,
    completed_deals INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    status VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    reject_reason VARCHAR(500),
    view_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    category_id BIGINT REFERENCES categories(id),
    university_id BIGINT REFERENCES universities(id),
    seller_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_images (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL
);

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    message_type VARCHAR(30) DEFAULT 'TEXT',
    offered_price NUMERIC(15, 0),
    meetup_location VARCHAR(255),
    meetup_time VARCHAR(100),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
