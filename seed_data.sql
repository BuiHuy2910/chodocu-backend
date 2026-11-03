-- =================================================================================
-- PASSUNI - SEED DATA (DỮ LIỆU MẪU)
-- Chạy SAU khi đã tạo schema:
--   psql "$SUPABASE_DB_URL" -f database_schema.sql
--   psql "$SUPABASE_DB_URL" -f seed_data.sql
-- Script này idempotent: chạy lại không nhân đôi dữ liệu.
-- =================================================================================

-- pgcrypto: dùng để băm mật khẩu demo ngay trong SQL (không hardcode hash)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================
-- 1. PLATFORM SETTINGS (phí sàn mặc định)
-- =====================
INSERT INTO platform_settings (setting_key, value, description) VALUES
('platform_fee_rate',   '0.05',    'Tỷ lệ phí sàn cơ bản (0.05 = 5%)'),
('min_platform_fee',    '2000',    'Phí sàn tối thiểu (VNĐ). Áp dụng khi % phí nhỏ hơn giá trị này'),
('max_platform_fee',    '50000',   'Phí sàn tối đa (VNĐ). 0 = không giới hạn'),
('fee_enabled',         'true',    'Bật/tắt tính phí sàn'),
('fee_payer',           'SELLER',  'Bên chịu phí: SELLER | BUYER | SPLIT'),
('fee_round_step',      '1000',    'Làm tròn phí sàn theo bước (VNĐ)'),
('app_name',            'PassUni', 'Tên ứng dụng hiển thị'),
('max_images_per_post', '5',       'Số ảnh tối đa mỗi bài đăng'),
('post_require_approval', 'true',  'Bài đăng cần admin duyệt trước khi hiển thị'),
('order_auto_cancel_hours', '72',  'Tự động hủy đơn PENDING sau N giờ')
ON CONFLICT (setting_key) DO NOTHING;

-- Bậc phí sàn mẫu (ưu tiên số lớn hơn khi khoảng giá trị trùng nhau)
INSERT INTO platform_fee_tiers (name, min_amount, max_amount, fee_rate, active, priority)
SELECT * FROM (VALUES
    ('Đơn nhỏ (< 100.000đ)',     0::NUMERIC,        100000::NUMERIC, 0.0300::NUMERIC, TRUE, 10),
    ('Đơn vừa (100k - 1 triệu)', 100000::NUMERIC,   1000000::NUMERIC, 0.0400::NUMERIC, TRUE, 20),
    ('Đơn lớn (> 1 triệu)',      1000000::NUMERIC,  NULL::NUMERIC,   0.0500::NUMERIC, TRUE, 30)
) AS v(name, min_amount, max_amount, fee_rate, active, priority)
WHERE NOT EXISTS (SELECT 1 FROM platform_fee_tiers);

-- =====================
-- 2. UNIVERSITIES
-- =====================
INSERT INTO universities (name, short_name, email_domain, address, safe_meetup_spots) VALUES
('KTX Khu B - ĐHQG TP.HCM',                'KTX Khu B',          'vnuhcm.edu.vn',  'Khu phố 6, P. Linh Trung, TP. Thủ Đức',     'Sảnh Tầng 1 Tòa B1, Cổng tin B4, Sảnh Nhà Văn Hóa Sinh Viên'),
('ĐH Bách Khoa - ĐHQG TP.HCM',             'ĐH Bách Khoa',       'hcmut.edu.vn',   '268 Lý Thường Kiệt / Làng ĐH Thủ Đức',     'Thư viện H1, Sảnh B1, Cổng chính Tô Hiến Thành'),
('ĐH Khoa Học Tự Nhiên - ĐHQG',            'ĐH Tự Nhiên',        'hcmus.edu.vn',   'Linh Trung, TP. Thủ Đức',                   'Sảnh nhà điều hành, Thư viện Tự Nhiên'),
('ĐH Công Nghệ Thông Tin (UIT)',            'ĐH CNTT - UIT',      'uit.edu.vn',     'Khu phố 6, P. Linh Trung, TP. Thủ Đức',     'Sảnh tòa A, Cổng tin UIT, Thư viện'),
('ĐH Kinh Tế - Luật (UEL)',                'ĐH Kinh Tế - Luật',  'uel.edu.vn',     'Quốc lộ 1A, Khu phố 3, P. Linh Xuân',       'Sảnh chính UEL, Khu tự học sinh viên')
ON CONFLICT (name) DO NOTHING;

-- =====================
-- 3. CATEGORIES
-- =====================
INSERT INTO categories (name, slug, icon, description) VALUES
('Giáo trình & Sách',        'giao-trinh-sach',   'menu_book',   'Giáo trình đại cương, sách chuyên ngành, tài liệu ôn thi'),
('Đồ điện & Quạt',           'do-dien-quat',      'mode_fan',    'Quạt đứng, nồi lẩu mini, ấm siêu tốc, đèn bàn học'),
('Đồ dùng & Bàn',            'do-dung-ban',       'desk',        'Bàn học gấp gọn, ghế xoay, kệ sách, tủ nhựa KTX'),
('Máy tính & Phụ kiện',      'may-tinh-phu-kien', 'laptop',      'Laptop, bàn phím cơ, chuột, màn hình, tai nghe'),
('Quần áo & Thời trang',     'quan-ao',           'checkroom',   'Quần áo còn mới, giày dép, túi xách, phụ kiện thời trang'),
('Xe & Phương tiện',         'xe-phuong-tien',    'two_wheeler', 'Xe đạp, xe máy điện, phụ tùng xe'),
('Thể thao & Gym',           'the-thao-gym',      'fitness_center', 'Dụng cụ tập gym, đồ thể thao, vợt cầu lông'),
('Khác',                     'khac',              'more_horiz',  'Các mặt hàng khác không thuộc danh mục trên')
ON CONFLICT (name) DO NOTHING;

-- =====================
-- 4. USERS (password: "password123" - băm bằng pgcrypto, không hardcode hash)
-- =====================
INSERT INTO users (email, password, full_name, student_id, phone, avatar_url, bio, university_id, dorm_area, role, verified_student, active, rating, completed_deals) VALUES
-- Admin
('admin@passuni.vn',
 crypt('password123', gen_salt('bf')),
 'Admin PassUni', 'ADMIN001', '0909000001',
 'https://api.dicebear.com/7.x/notionists/svg?seed=admin',
 'Quản trị viên hệ thống PassUni',
 1, 'Ban Quản lý', 'ADMIN', TRUE, TRUE, 5.0, 0),
-- Moderator
('mod@passuni.vn',
 crypt('password123', gen_salt('bf')),
 'Kiểm duyệt viên', 'MOD001', '0909000002',
 'https://api.dicebear.com/7.x/notionists/svg?seed=moderator',
 'Kiểm duyệt bài đăng trên PassUni',
 1, 'KTX Khu B', 'MODERATOR', TRUE, TRUE, 5.0, 0),
-- Students
('minhtu.nguyen@vnuhcm.edu.vn',
 crypt('password123', gen_salt('bf')),
 'Nguyễn Minh Thư', 'SV21120001', '0912345678',
 'https://api.dicebear.com/7.x/notionists/svg?seed=minhthu',
 'Sinh viên năm 3 - Chuyên ngành CNTT | Mình thường pass đồ cuối kỳ',
 1, 'KTX Khu B - Tòa B3', 'STUDENT', TRUE, TRUE, 4.9, 5),
('hoangyen@hcmut.edu.vn',
 crypt('password123', gen_salt('bf')),
 'Lê Hoàng Yến', '2014512', '0987654321',
 'https://api.dicebear.com/7.x/notionists/svg?seed=hoangyen',
 'K22 Bách Khoa | Hay pass sách & đồ dùng KTX 📚',
 2, 'KTX Khu B - Tòa A2', 'STUDENT', TRUE, TRUE, 5.0, 8),
('phuc.tran@uit.edu.vn',
 crypt('password123', gen_salt('bf')),
 'Trần Hoàng Phúc', '21520399', '0977123456',
 'https://api.dicebear.com/7.x/notionists/svg?seed=hoangphuc',
 'Dev UIT | Chuyên pass tech gadgets & phụ kiện máy tính 💻',
 4, 'Trọ gần UIT', 'STUDENT', TRUE, TRUE, 4.8, 12),
('ngocanh@hcmus.edu.vn',
 crypt('password123', gen_salt('bf')),
 'Phạm Ngọc Ánh', '22110088', '0963214785',
 'https://api.dicebear.com/7.x/notionists/svg?seed=ngocanh',
 'Tự Nhiên K23 | Hay mua đồ cũ giá tốt 🌿',
 3, 'KTX Khu B - Tòa C1', 'STUDENT', TRUE, TRUE, 4.7, 3),
('ducminh@uel.edu.vn',
 crypt('password123', gen_salt('bf')),
 'Võ Đức Minh', '20010256', '0945678123',
 'https://api.dicebear.com/7.x/notionists/svg?seed=ducminh',
 'UEL K20 sắp ra trường - pass hết đồ KTX',
 5, 'KTX Khu B - Tòa B5', 'STUDENT', TRUE, TRUE, 4.6, 7)
ON CONFLICT (email) DO NOTHING;

-- 4b. TÀI KHOẢN DEMO khớp với app mobile & trang admin
--     (mật khẩu băm bằng pgcrypto, không hardcode hash)
INSERT INTO users (email, password, full_name, student_id, phone, avatar_url, bio, university_id, dorm_area, role, verified_student, active, rating, completed_deals) VALUES
('admin@chodocu.edu.vn', crypt('admin123', gen_salt('bf')), 'Ban Quản Trị PassUni', 'ADMIN001', '0909000001',
 'https://api.dicebear.com/7.x/notionists/svg?seed=admin', 'Quản trị viên hệ thống PassUni',
 1, 'Ban Quản lý KTX', 'ADMIN', TRUE, TRUE, 5.0, 0),
('minhthu@student.vnuhcm.edu.vn', crypt('123456', gen_salt('bf')), 'Nguyễn Minh Thư', '21120501', '0912345678',
 'https://api.dicebear.com/7.x/notionists/svg?seed=minhthu', 'Sinh viên năm 3 - Chuyên ngành CNTT',
 1, 'KTX Khu B - Tòa B3', 'STUDENT', TRUE, TRUE, 4.9, 5),
('hoangphuc@uit.edu.vn', crypt('123456', gen_salt('bf')), 'Trần Hoàng Phúc', '21520399', '0938112233',
 'https://api.dicebear.com/7.x/notionists/svg?seed=hoangphuc', 'Dev UIT | Chuyên pass tech gadgets',
 4, 'Trọ gần UIT', 'STUDENT', TRUE, TRUE, 4.8, 12)
ON CONFLICT (email) DO NOTHING;

-- =====================
-- 5. PRODUCTS (mix PENDING & APPROVED để test workflow)
-- =====================
-- Lấy university_id và category_id từ tên để đảm bảo đúng
WITH ids AS (
    SELECT
        (SELECT id FROM universities WHERE short_name = 'KTX Khu B') as ktx_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa') as bk_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Tự Nhiên') as tn_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT') as uit_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Kinh Tế - Luật') as uel_id,
        (SELECT id FROM categories WHERE slug = 'giao-trinh-sach') as cat_sach_id,
        (SELECT id FROM categories WHERE slug = 'do-dien-quat') as cat_dien_id,
        (SELECT id FROM categories WHERE slug = 'do-dung-ban') as cat_ban_id,
        (SELECT id FROM categories WHERE slug = 'may-tinh-phu-kien') as cat_pc_id,
        (SELECT id FROM categories WHERE slug = 'the-thao-gym') as cat_sport_id,
        (SELECT id FROM users WHERE email = 'minhtu.nguyen@vnuhcm.edu.vn') as user_thu_id,
        (SELECT id FROM users WHERE email = 'hoangyen@hcmut.edu.vn') as user_yen_id,
        (SELECT id FROM users WHERE email = 'hoangphuc@uit.edu.vn') as user_phuc_id,
        (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn') as user_anh_id,
        (SELECT id FROM users WHERE email = 'ducminh@uel.edu.vn') as user_minh_id
)
INSERT INTO products (title, description, price, original_price, is_free, is_urgent, item_condition, condition_note, pass_reason, pickup_spot, status, view_count, favorite_count, category_id, university_id, seller_id)
SELECT
    p.title, p.description, p.price, p.original_price, p.is_free, p.is_urgent,
    p.item_condition, p.condition_note, p.pass_reason, p.pickup_spot, p.status,
    p.view_count, p.favorite_count,
    CASE p.cat_key
        WHEN 'sach' THEN ids.cat_sach_id
        WHEN 'dien' THEN ids.cat_dien_id
        WHEN 'ban' THEN ids.cat_ban_id
        WHEN 'pc' THEN ids.cat_pc_id
        WHEN 'sport' THEN ids.cat_sport_id
    END,
    CASE p.uni_key
        WHEN 'ktx' THEN ids.ktx_id
        WHEN 'bk' THEN ids.bk_id
        WHEN 'tn' THEN ids.tn_id
        WHEN 'uit' THEN ids.uit_id
        WHEN 'uel' THEN ids.uel_id
    END,
    CASE p.seller_key
        WHEN 'thu' THEN ids.user_thu_id
        WHEN 'yen' THEN ids.user_yen_id
        WHEN 'phuc' THEN ids.user_phuc_id
        WHEN 'anh' THEN ids.user_anh_id
        WHEN 'minh' THEN ids.user_minh_id
    END
FROM ids, (VALUES
    -- APPROVED products (đã duyệt, đang hiển thị)
    ('Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh',
     'Dùng đúng 1 học kỳ ôn thi đại cương, giữ kỷ hầu như không trầy xước. Khung chân kim loại chịu lực tốt. Bán kèm ghế nhựa. Ai ở KTX nhắn mình.',
     110000, 280000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 95% - Dọn phòng về quê',
     'Dọn phòng KTX / Chuyển trọ', 'Cổng tin Tòa B1 - KTX Khu B',
     'APPROVED', 82, 14, 'ban', 'ktx', 'thu'),
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm',
     'Quạt Senko chính hãng mua đầu năm, motor đồng chạy cực êm không rung ồn. Dùng được 1 năm, pass vì chuyển phòng có điều hòa rồi.',
     120000, 290000, FALSE, FALSE, 'GOOD_85', 'Mới 90% - Pass gấp trong chiều',
     'Ra trường / Tốt nghiệp', 'Sảnh Tòa H1 - ĐH Bách Khoa',
     'APPROVED', 67, 9, 'dien', 'bk', 'yen'),
    ('Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính ĐHQG',
     'Trọn bộ giáo trình Giải tích 1, 2 và Đại số tuyến tính chuẩn ĐHQG. Tặng kèm tập đề thi mẫu 5 năm gần nhất. Sách đẹp không gạch chân nhiều.',
     35000, 95000, FALSE, FALSE, 'LIKE_NEW_95', 'Sách đẹp - Tặng kèm đề thi',
     'Học xong môn / Qua môn', 'Thư viện Trung tâm ĐH Tự Nhiên',
     'APPROVED', 120, 22, 'sach', 'tn', 'anh'),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox',
     'Nồi lẩu điện mini Bear màu xanh mint siêu xinh, lòng nồi chống dính vân đá cao cấp. Dùng nấu mì, lẩu mini, hấp... trong KTX siêu tiện. Kèm xửng hấp inox và nắp kính.',
     110000, 250000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 98% - Dùng vài lần',
     'Chuyển trọ / Không nấu nữa', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'APPROVED', 95, 18, 'dien', 'uit', 'phuc'),
    ('Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB',
     'Laptop học tập văn phòng, code, thiết kế nhẹ. Pin còn trâu 4-5 tiếng. Bàn phím đầy đủ numpad, màn hình FHD viền mỏng. Bán vì lên đời MacBook.',
     5500000, 12000000, FALSE, TRUE, 'GOOD_85', 'Mới 85% - Còn bảo hành hãng',
     'Lên đời máy mới', 'Sảnh tòa B - KTX Khu B',
     'APPROVED', 210, 35, 'pc', 'ktx', 'minh'),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ',
     'Xe đạp địa hình khung nhôm siêu nhẹ, 21 tốc độ Shimano, phanh đĩa trước sau. Đã thay săm lốp mới, tra dầu chain. Đi được 2 năm còn chắc.',
     1200000, 2800000, FALSE, FALSE, 'GOOD_85', 'Mới 80% - Xe chắc chạy tốt',
     'Ra trường / Không dùng nữa', 'Sảnh KTX Khu B - Chỗ gửi xe',
     'APPROVED', 88, 11, 'sport', 'ktx', 'thu'),
    -- PENDING products (chờ duyệt)
    ('Máy tính bỏ túi Casio fx-580VN X chính hãng Bitex',
     'Máy tính quốc dân cho sinh viên kỹ thuật giải hệ phương trình, ma trận, tích phân siêu nhanh. Nút bấm nhảy, màn hình LCD rõ nét, còn tem chống giả Bitex.',
     280000, 650000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 96% - Còn tem Bitex',
     'Học xong môn / Qua môn', 'Sảnh thư viện H1 - ĐH Bách Khoa',
     'PENDING', 0, 0, 'sach', 'bk', 'yen'),
    ('Bàn phím cơ không dây Keychron K6 Red Switch (Pass nhanh)',
     'Bàn phím cơ nhỏ gọn 65% kết nối Bluetooth 3 thiết bị, gõ code cực sướng không gây ồn cho bạn cùng phòng KTX. Đủ cáp Type-C và keycap puller.',
     550000, 1450000, FALSE, TRUE, 'LIKE_NEW_95', 'Mới 95% - Pin trâu 2 tuần',
     'Lên đời đồ mới', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'PENDING', 0, 0, 'pc', 'uit', 'phuc'),
    ('Tai nghe Sony WH-1000XM4 chống ồn ANC (hộp đầy đủ)',
     'Tai nghe over-ear Sony flagship chống ồn cực tốt, âm thanh Hi-Res. Dùng học online, tập trung làm bài tập. Còn hộp, cáp, túi đựng đầy đủ.',
     3200000, 7000000, FALSE, FALSE, 'GOOD_85', 'Mới 88% - Hộp đầy đủ phụ kiện',
     'Lên đời tai nghe mới', 'Thư viện ĐH Tự Nhiên',
     'PENDING', 0, 0, 'pc', 'tn', 'anh'),
    ('Giáo trình Kinh tế Vi mô & Vĩ mô bản dịch tiếng Việt',
     'Bộ 2 cuốn giáo trình Kinh tế học vi mô và vĩ mô bản dịch tiếng Việt của Mankiw. Dùng học môn Kinh tế đại cương. Sách còn mới, gạch chân ít.',
     45000, 180000, FALSE, FALSE, 'GOOD_85', 'Còn tốt - Gạch chân ít',
     'Học xong môn', 'Sảnh chính UEL',
     'PENDING', 0, 0, 'sach', 'uel', 'minh')
) AS p(title, description, price, original_price, is_free, is_urgent, item_condition, condition_note, pass_reason, pickup_spot, status, view_count, favorite_count, cat_key, uni_key, seller_key)
WHERE NOT EXISTS (SELECT 1 FROM products existing WHERE existing.title = p.title);

-- =====================
-- 6. PRODUCT IMAGES
-- =====================
INSERT INTO product_images (product_id, image_url)
SELECT p.id, img.url
FROM products p
JOIN (VALUES
    ('Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh', 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?w=800&auto=format&fit=crop&q=80'),
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm', 'https://images.unsplash.com/photo-1618941716939-553df3c6c278?w=800&auto=format&fit=crop&q=80'),
    ('Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính ĐHQG', 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800&auto=format&fit=crop&q=80'),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox', 'https://images.unsplash.com/photo-1585515320310-259814833e62?w=800&auto=format&fit=crop&q=80'),
    ('Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB', 'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=800&auto=format&fit=crop&q=80'),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ', 'https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=800&auto=format&fit=crop&q=80'),
    ('Máy tính bỏ túi Casio fx-580VN X chính hãng Bitex', 'https://images.unsplash.com/photo-1587145820266-a5951ee6f620?w=800&auto=format&fit=crop&q=80'),
    ('Bàn phím cơ không dây Keychron K6 Red Switch (Pass nhanh)', 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&auto=format&fit=crop&q=80'),
    ('Tai nghe Sony WH-1000XM4 chống ồn ANC (hộp đầy đủ)', 'https://images.unsplash.com/photo-1583394838336-acd977736f90?w=800&auto=format&fit=crop&q=80'),
    ('Giáo trình Kinh tế Vi mô & Vĩ mô bản dịch tiếng Việt', 'https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=800&auto=format&fit=crop&q=80')
) AS img(title, url) ON p.title = img.title
WHERE NOT EXISTS (
    SELECT 1 FROM product_images pi
    WHERE pi.product_id = p.id AND pi.image_url = img.url
);

-- =====================
-- 7. ORDER REQUESTS MẪU
-- =====================
INSERT INTO order_requests (
    product_id, buyer_id, seller_id, offered_price, meetup_location, meetup_time, buyer_note,
    status, platform_fee_rate, platform_fee_amount, final_seller_amount, final_buyer_amount,
    fee_payer, payment_status, completed_at
)
SELECT
    (SELECT id FROM products WHERE title = 'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh' LIMIT 1),
    (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn'),
    (SELECT id FROM users WHERE email = 'minhtu.nguyen@vnuhcm.edu.vn'),
    110000,
    'Sảnh Tầng 1 Tòa B1 - KTX Khu B',
    '17:00 chiều nay',
    'Chiều 17h mình xuống sảnh B1 nhận bàn nhé!',
    'COMPLETED',
    0.04,
    4000,
    106000,
    110000,
    'SELLER',
    'COMPLETED',
    CURRENT_TIMESTAMP
WHERE EXISTS (SELECT 1 FROM products WHERE title = 'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh')
  AND NOT EXISTS (
      SELECT 1 FROM order_requests
      WHERE buyer_id = (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn')
        AND status = 'COMPLETED'
  );

-- Đơn đang chờ người bán duyệt (dùng để demo luồng duyệt đơn)
INSERT INTO order_requests (
    product_id, buyer_id, seller_id, offered_price, meetup_location, meetup_time, buyer_note,
    status, platform_fee_rate, platform_fee_amount, final_seller_amount, final_buyer_amount, fee_payer
)
SELECT
    p.id,
    (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn'),
    p.seller_id,
    p.price,
    COALESCE(p.pickup_spot, 'Sảnh KTX'),
    '18:30 hôm nay',
    'Mình qua xem trực tiếp được không ạ?',
    'PENDING',
    0.04,
    ROUND(p.price * 0.04, 0),
    p.price - ROUND(p.price * 0.04, 0),
    p.price,
    'SELLER'
FROM products p
WHERE p.status = 'APPROVED'
  AND p.seller_id <> (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn')
  AND NOT EXISTS (SELECT 1 FROM order_requests o2 WHERE o2.product_id = p.id)
LIMIT 1;

-- Sinh mã đơn hiển thị cho các đơn đã tạo
UPDATE order_requests
SET order_code = 'PD' || LPAD(id::TEXT, 6, '0')
WHERE order_code IS NULL;

-- =====================
-- 8. TRANSACTIONS MẪU (từ đơn COMPLETED)
-- =====================
INSERT INTO transactions (order_id, buyer_id, seller_id, product_id, transaction_amount, platform_fee_rate, platform_fee_amount, seller_receive_amount)
SELECT
    o.id,
    o.buyer_id,
    o.seller_id,
    o.product_id,
    o.offered_price,
    o.platform_fee_rate,
    o.platform_fee_amount,
    o.final_seller_amount
FROM order_requests o
WHERE o.status = 'COMPLETED'
  AND o.payment_status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.order_id = o.id);

-- =====================
-- 9. WISHLISTS MẪU
-- =====================
INSERT INTO wishlists (user_id, product_id)
SELECT
    (SELECT id FROM users WHERE email = 'minhtu.nguyen@vnuhcm.edu.vn'),
    id
FROM products WHERE status = 'APPROVED' LIMIT 3
ON CONFLICT (user_id, product_id) DO NOTHING;

-- =====================
-- 10. NOTIFICATIONS MẪU
-- =====================
INSERT INTO notifications (user_id, type, title, message, reference_id, is_read)
SELECT
    (SELECT id FROM users WHERE email = 'hoangyen@hcmut.edu.vn'),
    'PRODUCT_APPROVED',
    'Tin đăng đã được duyệt! 🎉',
    'Tin "Quạt đứng Senko lồng sắt 3 tốc độ chạy êm" đã được admin duyệt và hiển thị trên sàn.',
    (SELECT id FROM products WHERE title LIKE 'Quạt đứng Senko%' LIMIT 1),
    TRUE
WHERE EXISTS (SELECT 1 FROM users WHERE email = 'hoangyen@hcmut.edu.vn')
  AND NOT EXISTS (
      SELECT 1 FROM notifications n
      WHERE n.type = 'PRODUCT_APPROVED' AND n.message LIKE 'Tin "Quạt đứng Senko%'
  );

-- =====================
-- VERIFICATION
-- =====================
SELECT 'universities' as table_name, COUNT(*) as count FROM universities
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'platform_settings', COUNT(*) FROM platform_settings
UNION ALL SELECT 'transactions', COUNT(*) FROM transactions;
