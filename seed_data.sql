-- =================================================================================
-- PASSUNI - SEED DATA (DỮ LIỆU MẪU) - bản mở rộng
-- Chạy SAU khi đã tạo schema:
--   psql "$SUPABASE_DB_URL" -f database_schema.sql
--   psql "$SUPABASE_DB_URL" -f seed_data.sql
-- Script này idempotent: chạy lại không nhân đôi dữ liệu.
--
-- Lưu ý về ảnh:
--   * Avatar dùng i.pravatar.cc (JPEG) — Coil trong app KHÔNG giải mã được SVG,
--     nên tuyệt đối không dùng api.dicebear.com/.../svg cho avatar.
--   * Ảnh sản phẩm dùng images.unsplash.com (JPEG).
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
-- 2. UNIVERSITIES (8 trường/khu vực)
-- =====================
INSERT INTO universities (name, short_name, email_domain, address, safe_meetup_spots) VALUES
('KTX Khu B - ĐHQG TP.HCM',        'KTX Khu B',          'vnuhcm.edu.vn',  'Khu phố 6, P. Linh Trung, TP. Thủ Đức',  'Sảnh Tầng 1 Tòa B1, Cổng tin B4, Sảnh Nhà Văn Hóa Sinh Viên'),
('ĐH Bách Khoa - ĐHQG TP.HCM',     'ĐH Bách Khoa',       'hcmut.edu.vn',   '268 Lý Thường Kiệt / Làng ĐH Thủ Đức',   'Thư viện H1, Sảnh B1, Cổng chính Tô Hiến Thành'),
('ĐH Khoa Học Tự Nhiên - ĐHQG',    'ĐH Tự Nhiên',        'hcmus.edu.vn',   'Linh Trung, TP. Thủ Đức',                'Sảnh nhà điều hành, Thư viện Tự Nhiên'),
('ĐH Công Nghệ Thông Tin (UIT)',   'ĐH CNTT - UIT',      'uit.edu.vn',     'Khu phố 6, P. Linh Trung, TP. Thủ Đức',  'Sảnh tòa A, Cổng tin UIT, Thư viện'),
('ĐH Kinh Tế - Luật (UEL)',        'ĐH Kinh Tế - Luật',  'uel.edu.vn',     'Quốc lộ 1A, Khu phố 3, P. Linh Xuân',    'Sảnh chính UEL, Khu tự học sinh viên'),
('ĐH Tôn Đức Thắng (TDTU)',        'ĐH Tôn Đức Thắng',   'tdtu.edu.vn',    '19 Nguyễn Hữu Thọ, Q.7, TP.HCM',         'Sảnh Tòa A, Căng tin TDT, Thư viện'),
('ĐH Sư Phạm Kỹ Thuật TP.HCM',     'ĐH Sư Phạm Kỹ Thuật','hcmute.edu.vn',  '1 Võ Văn Ngân, TP. Thủ Đức',             'Sảnh trung tâm, KTX Sư Phạm Kỹ Thuật'),
('ĐH Y Dược TP.HCM',               'ĐH Y Dược',          'ump.edu.vn',     '217 Hồng Bàng, Q.5, TP.HCM',             'Sảnh A, Thư viện Y Dược')
ON CONFLICT (name) DO NOTHING;

-- =====================
-- 3. CATEGORIES (8)
-- =====================
INSERT INTO categories (name, slug, icon, description) VALUES
('Giáo trình & Sách',        'giao-trinh-sach',   'menu_book',      'Giáo trình đại cương, sách chuyên ngành, tài liệu ôn thi'),
('Đồ điện & Quạt',           'do-dien-quat',      'mode_fan',       'Quạt đứng, nồi lẩu mini, ấm siêu tốc, đèn bàn học'),
('Đồ dùng & Bàn',            'do-dung-ban',       'desk',           'Bàn học gấp gọn, ghế xoay, kệ sách, tủ nhựa KTX'),
('Máy tính & Phụ kiện',      'may-tinh-phu-kien', 'laptop',         'Laptop, bàn phím cơ, chuột, màn hình, tai nghe'),
('Quần áo & Thời trang',     'quan-ao',           'checkroom',      'Quần áo còn mới, giày dép, túi xách, phụ kiện thời trang'),
('Xe & Phương tiện',         'xe-phuong-tien',    'two_wheeler',    'Xe đạp, xe máy điện, phụ tùng xe'),
('Thể thao & Gym',           'the-thao-gym',      'fitness_center', 'Dụng cụ tập gym, đồ thể thao, vợt cầu lông'),
('Khác',                     'khac',              'more_horiz',     'Các mặt hàng khác không thuộc danh mục trên')
ON CONFLICT (name) DO NOTHING;

-- =====================
-- 4. USERS — 20 tài khoản mật khẩu "password123"
--    Avatar: i.pravatar.cc (JPEG) — đa dạng giới tính / độ tuổi / phong cách
-- =====================
INSERT INTO users (email, password, full_name, student_id, phone, avatar_url, bio, university_id, dorm_area, role, verified_student, active, rating, completed_deals) VALUES
-- Ban quản trị
('admin@passuni.vn', crypt('password123', gen_salt('bf')), 'Nguyễn Quốc Bảo', 'ADMIN001', '0909000001',
 'https://i.pravatar.cc/300?img=12', 'Quản trị viên hệ thống PassUni',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'Ban Quản lý KTX', 'ADMIN', TRUE, TRUE, 5.0, 0),
('mod@passuni.vn', crypt('password123', gen_salt('bf')), 'Lê Kiểm Duyệt', 'MOD001', '0909000002',
 'https://i.pravatar.cc/300?img=33', 'Kiểm duyệt viên nội dung PassUni',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'KTX Khu B - Tòa B2', 'MODERATOR', TRUE, TRUE, 5.0, 0),
-- Sinh viên
('minhtu.nguyen@vnuhcm.edu.vn', crypt('password123', gen_salt('bf')), 'Nguyễn Minh Thư', 'SV21120001', '0912345678',
 'https://i.pravatar.cc/300?img=47', 'Sinh viên năm 3 CNTT | Chuyên pass đồ cuối kỳ, sách giáo trình',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'KTX Khu B - Tòa B3', 'STUDENT', TRUE, TRUE, 4.9, 8),
('hoangyen@hcmut.edu.vn', crypt('password123', gen_salt('bf')), 'Lê Hoàng Yến', '2014512', '0987654321',
 'https://i.pravatar.cc/300?img=44', 'K22 Bách Khoa | Hay pass sách & đồ dùng KTX',
 (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa'), 'KTX Khu B - Tòa A2', 'STUDENT', TRUE, TRUE, 5.0, 12),
('phuc.tran@uit.edu.vn', crypt('password123', gen_salt('bf')), 'Trần Hoàng Phúc', '21520399', '0977123456',
 'https://i.pravatar.cc/300?img=13', 'Dev UIT | Chuyên pass tech gadgets & phụ kiện máy tính',
 (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT'), 'Trọ gần UIT', 'STUDENT', TRUE, TRUE, 4.8, 15),
('ngocanh@hcmus.edu.vn', crypt('password123', gen_salt('bf')), 'Phạm Ngọc Ánh', '22110088', '0963214785',
 'https://i.pravatar.cc/300?img=45', 'Tự Nhiên K23 | Hay mua đồ cũ giá tốt',
 (SELECT id FROM universities WHERE short_name = 'ĐH Tự Nhiên'), 'KTX Khu B - Tòa C1', 'STUDENT', TRUE, TRUE, 4.7, 6),
('ducminh@uel.edu.vn', crypt('password123', gen_salt('bf')), 'Võ Đức Minh', '20010256', '0945678123',
 'https://i.pravatar.cc/300?img=15', 'UEL K20 sắp ra trường - pass hết đồ KTX',
 (SELECT id FROM universities WHERE short_name = 'ĐH Kinh Tế - Luật'), 'KTX Khu B - Tòa B5', 'STUDENT', TRUE, TRUE, 4.6, 9),
('thutrang@hcmut.edu.vn', crypt('password123', gen_salt('bf')), 'Đặng Thị Thu Trang', '2110789', '0932111222',
 'https://i.pravatar.cc/300?img=20', 'Bách Khoa K21 | Thích đồ gọn xinh cho phòng trọ',
 (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa'), 'KTX Khu B - Tòa A1', 'STUDENT', TRUE, TRUE, 4.8, 5),
('quanghuy@uit.edu.vn', crypt('password123', gen_salt('bf')), 'Nguyễn Quang Huy', '21801234', '0918222333',
 'https://i.pravatar.cc/300?img=51', 'UIT K21 | Mê gear máy tính, hay đổi thiết bị',
 (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT'), 'KTX Khu B - Tòa B4', 'STUDENT', TRUE, TRUE, 4.5, 4),
('mailinh@hcmus.edu.vn', crypt('password123', gen_salt('bf')), 'Trần Mai Linh', '22115566', '0907333444',
 'https://i.pravatar.cc/300?img=26', 'Tự Nhiên K22 | Bán sách ôn thi, đồ thí nghiệm cũ',
 (SELECT id FROM universities WHERE short_name = 'ĐH Tự Nhiên'), 'KTX Khu B - Tòa C2', 'STUDENT', TRUE, TRUE, 4.9, 7),
('trongnghia@uel.edu.vn', crypt('password123', gen_salt('bf')), 'Lê Trọng Nghĩa', '20905432', '0944555666',
 'https://i.pravatar.cc/300?img=53', 'UEL K20 | Pass đồ thể thao và sách kinh tế',
 (SELECT id FROM universities WHERE short_name = 'ĐH Kinh Tế - Luật'), 'Trọ Bình Thạnh', 'STUDENT', TRUE, TRUE, 4.4, 3),
('khanhlinh@vnuhcm.edu.vn', crypt('password123', gen_salt('bf')), 'Bùi Khánh Linh', '22120099', '0966777888',
 'https://i.pravatar.cc/300?img=16', 'KTX Khu B | Đồ handmade & trang trí phòng',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'KTX Khu B - Tòa B6', 'STUDENT', TRUE, TRUE, 5.0, 4),
('minhkhang@hcmut.edu.vn', crypt('password123', gen_salt('bf')), 'Phan Minh Khang', '20151234', '0977888999',
 'https://i.pravatar.cc/300?img=57', 'Bách Khoa K20 | Bán đồ tập gym, xe đạp đi học',
 (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa'), 'KTX Khu B - Tòa A3', 'STUDENT', TRUE, TRUE, 4.7, 11),
('thaovy@uit.edu.vn', crypt('password123', gen_salt('bf')), 'Nguyễn Thảo Vy', '21809999', '0913999888',
 'https://i.pravatar.cc/300?img=48', 'UIT K22 | Bán quần áo, giày dép còn mới',
 (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT'), 'KTX Khu B - Tòa B1', 'STUDENT', TRUE, TRUE, 4.6, 6),
('dinhduy@hcmus.edu.vn', crypt('password123', gen_salt('bf')), 'Hoàng Đình Duy', '22117777', '0938111777',
 'https://i.pravatar.cc/300?img=59', 'Tự Nhiên K22 | Nhiếp ảnh nghiệp dư, hay thanh lý máy ảnh',
 (SELECT id FROM universities WHERE short_name = 'ĐH Tự Nhiên'), 'KTX Khu B - Tòa C3', 'STUDENT', TRUE, TRUE, 4.8, 5),
('thanhtruc@uel.edu.vn', crypt('password123', gen_salt('bf')), 'Lý Thanh Trúc', '20908888', '0968222666',
 'https://i.pravatar.cc/300?img=24', 'UEL K20 | Đồ gia dụng nhỏ cho phòng trọ',
 (SELECT id FROM universities WHERE short_name = 'ĐH Kinh Tế - Luật'), 'Trọ Thủ Đức', 'STUDENT', TRUE, TRUE, 4.5, 2),
('tuananh@vnuhcm.edu.vn', crypt('password123', gen_salt('bf')), 'Đỗ Tuấn Anh', '22128123', '0909444555',
 'https://i.pravatar.cc/300?img=60', 'KTX Khu B | Hay pass đồ điện gia dụng',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'KTX Khu B - Tòa B4', 'STUDENT', TRUE, TRUE, 4.3, 3),
('ngocdiep@hcmut.edu.vn', crypt('password123', gen_salt('bf')), 'Dương Ngọc Diệp', '20153333', '0945777333',
 'https://i.pravatar.cc/300?img=21', 'Bách Khoa K20 | Sách chuyên ngành và dụng cụ học tập',
 (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa'), 'KTX Khu B - Tòa A2', 'STUDENT', TRUE, TRUE, 4.9, 8),
('baolong@tdtu.edu.vn', crypt('password123', gen_salt('bf')), 'Trịnh Bảo Long', '21701122', '0912333000',
 'https://i.pravatar.cc/300?img=68', 'TDTU K21 | Đồ công nghệ, tai nghe, loa',
 (SELECT id FROM universities WHERE short_name = 'ĐH Tôn Đức Thắng'), 'KTX TDT', 'STUDENT', TRUE, TRUE, 4.6, 5),
('huonggiang@hcmute.edu.vn', crypt('password123', gen_salt('bf')), 'Mai Hương Giang', '22109911', '0978123222',
 'https://i.pravatar.cc/300?img=1', 'SPKT K22 | Đồ bếp mini và đồ dùng KTX',
 (SELECT id FROM universities WHERE short_name = 'ĐH Sư Phạm Kỹ Thuật'), 'KTX SPKT', 'STUDENT', TRUE, TRUE, 4.7, 4)
ON CONFLICT (email) DO NOTHING;

-- =====================
-- 4b. TÀI KHOẢN DEMO khớp với app mobile & trang admin (mật khẩu khác)
-- =====================
INSERT INTO users (email, password, full_name, student_id, phone, avatar_url, bio, university_id, dorm_area, role, verified_student, active, rating, completed_deals) VALUES
('admin@chodocu.edu.vn', crypt('admin123', gen_salt('bf')), 'Ban Quản Trị PassUni', 'ADMIN002', '0909000003',
 'https://i.pravatar.cc/300?img=11', 'Tài khoản quản trị dùng cho trang admin',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'Ban Quản lý KTX', 'ADMIN', TRUE, TRUE, 5.0, 0),
('minhthu@student.vnuhcm.edu.vn', crypt('123456', gen_salt('bf')), 'Nguyễn Minh Thư', '21120501', '0912345678',
 'https://i.pravatar.cc/300?img=47', 'Tài khoản demo người bán',
 (SELECT id FROM universities WHERE short_name = 'KTX Khu B'), 'KTX Khu B - Tòa B3', 'STUDENT', TRUE, TRUE, 4.9, 8),
('hoangphuc@uit.edu.vn', crypt('123456', gen_salt('bf')), 'Trần Hoàng Phúc', '21520399', '0938112233',
 'https://i.pravatar.cc/300?img=13', 'Tài khoản demo người mua',
 (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT'), 'Trọ gần UIT', 'STUDENT', TRUE, TRUE, 4.8, 15)
ON CONFLICT (email) DO NOTHING;

-- =====================
-- 5. PRODUCTS — 25 tin, trải đều 8 danh mục và 5 trạng thái
-- =====================
WITH ids AS (
    SELECT
        (SELECT id FROM universities WHERE short_name = 'KTX Khu B') as ktx_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Bách Khoa') as bk_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Tự Nhiên') as tn_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH CNTT - UIT') as uit_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Kinh Tế - Luật') as uel_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Tôn Đức Thắng') as tdt_id,
        (SELECT id FROM universities WHERE short_name = 'ĐH Sư Phạm Kỹ Thuật') as spkt_id,
        (SELECT id FROM categories WHERE slug = 'giao-trinh-sach') as cat_sach_id,
        (SELECT id FROM categories WHERE slug = 'do-dien-quat') as cat_dien_id,
        (SELECT id FROM categories WHERE slug = 'do-dung-ban') as cat_ban_id,
        (SELECT id FROM categories WHERE slug = 'may-tinh-phu-kien') as cat_pc_id,
        (SELECT id FROM categories WHERE slug = 'quan-ao') as cat_ao_id,
        (SELECT id FROM categories WHERE slug = 'xe-phuong-tien') as cat_xe_id,
        (SELECT id FROM categories WHERE slug = 'the-thao-gym') as cat_sport_id,
        (SELECT id FROM categories WHERE slug = 'khac') as cat_khac_id,
        (SELECT id FROM users WHERE email = 'minhtu.nguyen@vnuhcm.edu.vn') as u_thu,
        (SELECT id FROM users WHERE email = 'hoangyen@hcmut.edu.vn') as u_yen,
        (SELECT id FROM users WHERE email = 'phuc.tran@uit.edu.vn') as u_phuc,
        (SELECT id FROM users WHERE email = 'ngocanh@hcmus.edu.vn') as u_anh,
        (SELECT id FROM users WHERE email = 'ducminh@uel.edu.vn') as u_minh,
        (SELECT id FROM users WHERE email = 'thutrang@hcmut.edu.vn') as u_trang,
        (SELECT id FROM users WHERE email = 'quanghuy@uit.edu.vn') as u_huy,
        (SELECT id FROM users WHERE email = 'mailinh@hcmus.edu.vn') as u_linh,
        (SELECT id FROM users WHERE email = 'trongnghia@uel.edu.vn') as u_nghia,
        (SELECT id FROM users WHERE email = 'khanhlinh@vnuhcm.edu.vn') as u_klinh,
        (SELECT id FROM users WHERE email = 'minhkhang@hcmut.edu.vn') as u_khang,
        (SELECT id FROM users WHERE email = 'thaovy@uit.edu.vn') as u_vy,
        (SELECT id FROM users WHERE email = 'dinhduy@hcmus.edu.vn') as u_duy,
        (SELECT id FROM users WHERE email = 'thanhtruc@uel.edu.vn') as u_truc,
        (SELECT id FROM users WHERE email = 'tuananh@vnuhcm.edu.vn') as u_tuan,
        (SELECT id FROM users WHERE email = 'ngocdiep@hcmut.edu.vn') as u_diep,
        (SELECT id FROM users WHERE email = 'baolong@tdtu.edu.vn') as u_long,
        (SELECT id FROM users WHERE email = 'huonggiang@hcmute.edu.vn') as u_giang
)
INSERT INTO products (title, description, price, original_price, is_free, is_urgent, item_condition, condition_note, pass_reason, pickup_spot, status, view_count, favorite_count, category_id, university_id, seller_id)
SELECT
    p.title, p.description, p.price, p.original_price, p.is_free, p.is_urgent,
    p.item_condition, p.condition_note, p.pass_reason, p.pickup_spot, p.status,
    p.view_count, p.favorite_count,
    CASE p.cat_key
        WHEN 'sach'  THEN ids.cat_sach_id
        WHEN 'dien'  THEN ids.cat_dien_id
        WHEN 'ban'   THEN ids.cat_ban_id
        WHEN 'pc'    THEN ids.cat_pc_id
        WHEN 'ao'    THEN ids.cat_ao_id
        WHEN 'xe'    THEN ids.cat_xe_id
        WHEN 'sport' THEN ids.cat_sport_id
        WHEN 'khac'  THEN ids.cat_khac_id
    END,
    CASE p.uni_key
        WHEN 'ktx'  THEN ids.ktx_id
        WHEN 'bk'   THEN ids.bk_id
        WHEN 'tn'   THEN ids.tn_id
        WHEN 'uit'  THEN ids.uit_id
        WHEN 'uel'  THEN ids.uel_id
        WHEN 'tdt'  THEN ids.tdt_id
        WHEN 'spkt' THEN ids.spkt_id
    END,
    CASE p.seller_key
        WHEN 'thu'   THEN ids.u_thu
        WHEN 'yen'   THEN ids.u_yen
        WHEN 'phuc'  THEN ids.u_phuc
        WHEN 'anh'   THEN ids.u_anh
        WHEN 'minh'  THEN ids.u_minh
        WHEN 'trang' THEN ids.u_trang
        WHEN 'huy'   THEN ids.u_huy
        WHEN 'linh'  THEN ids.u_linh
        WHEN 'nghia' THEN ids.u_nghia
        WHEN 'klinh' THEN ids.u_klinh
        WHEN 'khang' THEN ids.u_khang
        WHEN 'vy'    THEN ids.u_vy
        WHEN 'duy'   THEN ids.u_duy
        WHEN 'truc'  THEN ids.u_truc
        WHEN 'tuan'  THEN ids.u_tuan
        WHEN 'diep'  THEN ids.u_diep
        WHEN 'long'  THEN ids.u_long
        WHEN 'giang' THEN ids.u_giang
    END
FROM ids, (VALUES
    -- ============ APPROVED (14) — hiển thị công khai ============
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm',
     'Quạt Senko chính hãng mua đầu năm, motor đồng chạy cực êm không rung ồn. Dùng được 1 năm, pass vì chuyển phòng có điều hòa rồi.',
     120000, 290000, FALSE, FALSE, 'GOOD_85', 'Mới 90% - Pass gấp trong chiều',
     'Ra trường / Tốt nghiệp', 'Sảnh Tòa H1 - ĐH Bách Khoa',
     'APPROVED', 167, 29, 'dien', 'bk', 'yen'),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox',
     'Nồi lẩu điện mini Bear màu xanh mint siêu xinh, lòng nồi chống dính vân đá cao cấp. Dùng nấu mì, lẩu mini, hấp trong KTX siêu tiện. Kèm xửng hấp inox và nắp kính.',
     110000, 250000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 98% - Dùng vài lần',
     'Chuyển trọ / Không nấu nữa', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'APPROVED', 195, 38, 'dien', 'uit', 'phuc'),
    ('Đèn học chống cận LED 3 chế độ ánh sáng',
     'Đèn bàn LED chống cận, 3 chế độ sáng (vàng - trung tính - trắng), có cổng USB sạc điện thoại. Kẹp được mép bàn hoặc để đế, rất tiện cho phòng KTX chật.',
     85000, 210000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 95% - Còn hộp',
     'Chuyển trọ', 'Cổng tin Tòa A1 - KTX Khu B',
     'APPROVED', 143, 21, 'dien', 'ktx', 'trang'),
    ('Kệ nhựa đầu giường 3 tầng có bánh xe',
     'Kệ nhựa 3 tầng chịu lực tốt, có 4 bánh xe di chuyển dễ, vừa in giường KTX. Đựng được sách, mỹ phẩm, đồ lặt vặt. Còn mới vì mới dùng 1 học kỳ.',
     95000, 240000, FALSE, FALSE, 'GOOD_85', 'Mới 90% - Sạch sẽ',
     'Dọn phòng về quê', 'Sảnh Tầng 1 Tòa B1 - KTX Khu B',
     'APPROVED', 88, 14, 'ban', 'ktx', 'klinh'),
    ('Ghế xoay văn phòng lưng lưới chân sao',
     'Ghế xoay lưng lưới thoáng, tựa đầu và tay vịn có thể nâng hạ. Ngồi học cả ngày không mỏi. Xước nhẹ ở chân, còn lại chắc chắn.',
     420000, 950000, FALSE, FALSE, 'GOOD_85', 'Mới 80% - Xước nhẹ chân',
     'Lên đời ghế công thái học', 'KTX Khu B - Tòa A3',
     'APPROVED', 240, 41, 'ban', 'bk', 'khang'),
    ('Áo khoác gió 2 lớp chống nước size L',
     'Áo khoác gió nam/nữ 2 lớp, chống nước nhẹ, có mũ trùm, túi khóa kéo kín. Mặc đi học mùa mưa rất ổn. Đã giặt sạch, không mùi.',
     130000, 350000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 95% - Đã giặt',
     'Mua nhầm size', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'APPROVED', 132, 17, 'ao', 'uit', 'vy'),
    ('Giày sneaker trắng size 41 chính hãng',
     'Sneaker trắng basic, size 41, đi đúng 3 lần vì bị chật. Đế còn trắng, không bong keo. Có hộp và túi giấy.',
     350000, 890000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 96% - Như hình',
     'Bị chật size', 'Thư viện ĐH Tự Nhiên',
     'APPROVED', 210, 34, 'ao', 'tn', 'duy'),
    ('Máy tính Casio fx-570VN Plus kèm bao da',
     'Máy tính bỏ túi quốc dân cho sinh viên, phím nhạy, màn hình rõ, pin mới thay. Kèm bao da và hướng dẫn sử dụng.',
     160000, 420000, FALSE, FALSE, 'GOOD_85', 'Mới 85% - Pin mới thay',
     'Đã tốt nghiệp môn Toán', 'Sảnh chính UEL',
     'APPROVED', 96, 12, 'sach', 'uel', 'nghia'),
    ('Giáo trình Lập trình Java & Cấu trúc dữ liệu',
     'Combo 2 cuốn: Lập trình Java căn bản và Cấu trúc dữ liệu & Giải thuật. Sách của UIT, không gạch chân, có ghi chú bút chì ở vài trang.',
     90000, 260000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 95% - Ghi chú bút chì',
     'Đã qua môn', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'APPROVED', 178, 27, 'sach', 'uit', 'vy'),
    ('Chuột không dây Logitech M331 Silent',
     'Chuột không dây im lặng, kết nối USB receiver, pin AA dùng được 1 năm. Cầm chắc tay, phù hợp học online và làm việc.',
     150000, 390000, FALSE, FALSE, 'GOOD_85', 'Mới 88% - Kèm receiver',
     'Chuyển sang chuột gaming', 'Thư viện H1 - ĐH Bách Khoa',
     'APPROVED', 121, 19, 'pc', 'bk', 'trang'),
    ('Màn hình Dell 24 inch FHD viền mỏng',
     'Màn hình Dell P2419H 24 inch Full HD, IPS, có HDMI + DisplayPort, chân đế xoay nâng hạ. Không điểm chết, kèm dây nguồn và HDMI.',
     1350000, 3200000, FALSE, FALSE, 'GOOD_85', 'Mới 85% - Không điểm chết',
     'Mua màn hình 27 inch', 'KTX Khu B - Tòa B4',
     'APPROVED', 305, 52, 'pc', 'uit', 'huy'),
    ('Vợt cầu lông Yonex chính hãng kèm bao',
     'Vợt Yonex Nanoray, căng lưới 10.5kg, tay cầm mới quấn. Đánh đôi rất ổn. Kèm bao vợt và 2 quả cầu.',
     380000, 850000, FALSE, FALSE, 'GOOD_85', 'Mới 85% - Lưới còn căng',
     'Chuyển sang bộ môn khác', 'KTX Khu B - Sân cầu lông',
     'APPROVED', 143, 23, 'sport', 'ktx', 'tuan'),
    ('Bộ tạ tay 2x5kg kèm thanh đòn',
     'Bộ tạ tay 5kg x2 bọc cao su, kèm thanh đòn nối. Tập tại phòng hoặc KTX đều được. Cao su không bị bong, chỉ hơi bám bụi.',
     280000, 650000, FALSE, FALSE, 'GOOD_85', 'Mới 82% - Chắc chắn',
     'Đi tập gym nên không dùng', 'KTX Khu B - Tòa A3',
     'APPROVED', 189, 30, 'sport', 'bk', 'khang'),
    ('Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm',
     'Máy ảnh mirrorless Fujifilm X-A5, cảm biến APS-C, kèm lens kit 15-45mm chống rung. Chụp được 12.000 shot, màn hình lật cảm ứng. Có sạc và 1 pin dự phòng.',
     5500000, 13500000, FALSE, TRUE, 'GOOD_85', 'Mới 85% - 12k shot',
     'Chuyển sang máy full-frame', 'Thư viện ĐH Tự Nhiên',
     'APPROVED', 512, 88, 'khac', 'tn', 'duy'),

    -- ============ PENDING (6) — chờ admin duyệt ============
    ('Máy tính bỏ túi Casio fx-580VN X chính hãng Bitex',
     'Máy tính quốc dân cho sinh viên kỹ thuật, giải hệ phương trình, ma trận, tích phân rất nhanh. Nút bấm nhảy, còn tem chống giả Bitex.',
     280000, 650000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 96% - Còn tem Bitex',
     'Học xong môn / Qua môn', 'Sảnh thư viện H1 - ĐH Bách Khoa',
     'PENDING', 0, 0, 'sach', 'bk', 'yen'),
    ('Bàn phím cơ không dây Keychron K6 Red Switch',
     'Bàn phím cơ 65% kết nối Bluetooth 3 thiết bị, gõ code cực sướng mà không ồn cho bạn cùng phòng. Đủ cáp Type-C và keycap puller.',
     550000, 1450000, FALSE, TRUE, 'LIKE_NEW_95', 'Mới 95% - Pin trâu 2 tuần',
     'Lên đời keychron khác', 'Sảnh Tòa A - ĐH CNTT (UIT)',
     'PENDING', 0, 0, 'pc', 'uit', 'phuc'),
    ('Tai nghe Sony WH-1000XM4 chống ồn ANC',
     'Tai nghe over-ear flagship, chống ồn cực tốt, âm thanh Hi-Res. Dùng học online và tự học. Còn hộp, cáp, túi đựng đầy đủ.',
     3200000, 7000000, FALSE, FALSE, 'GOOD_85', 'Mới 88% - Đủ phụ kiện',
     'Lên đời tai nghe mới', 'Thư viện ĐH Tự Nhiên',
     'PENDING', 0, 0, 'pc', 'tn', 'anh'),
    ('Giáo trình Kinh tế Vi mô & Vĩ mô bản dịch tiếng Việt',
     'Bộ 2 cuốn giáo trình Mankiw bản dịch tiếng Việt. Sách còn mới, gạch chân ít bằng bút chì.',
     45000, 180000, FALSE, FALSE, 'GOOD_85', 'Còn tốt - Gạch chân ít',
     'Học xong môn', 'Sảnh chính UEL',
     'PENDING', 0, 0, 'sach', 'uel', 'minh'),
    ('Tủ lạnh mini 50L cho phòng trọ',
     'Tủ lạnh mini 50L, làm lạnh nhanh, tiết kiệm điện, phù hợp 1-2 người. Hoạt động êm, đã vệ sinh sạch. Có thể giao trong KTX Khu B.',
     1200000, 2900000, FALSE, FALSE, 'GOOD_85', 'Mới 85% - Chạy êm',
     'Chuyển sang tủ lớn hơn', 'KTX Khu B - Tòa B4',
     'PENDING', 0, 0, 'dien', 'ktx', 'tuan'),
    ('Bàn học gấp gọn chân sắt 80x40cm',
     'Bàn học gấp gọn chân sắt sơn tĩnh điện, mặt gỗ công nghiệp. Gấp lại được, tiện mang theo khi chuyển trọ. Chân bàn chắc chắn.',
     220000, 520000, FALSE, FALSE, 'GOOD_85', 'Mới 80% - Chắc chắn',
     'Chuyển trọ', 'Trọ Thủ Đức',
     'PENDING', 0, 0, 'ban', 'uel', 'truc'),

    -- ============ SOLD (3) — đã bán qua đơn COMPLETED ============
    ('Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh',
     'Dùng đúng 1 học kỳ ôn thi đại cương, giữ kỹ hầu như không trầy xước. Khung chân kim loại chịu lực tốt. Bán kèm ghế nhựa.',
     110000, 280000, FALSE, FALSE, 'LIKE_NEW_95', 'Mới 95% - Dọn phòng về quê',
     'Dọn phòng KTX / Chuyển trọ', 'Cổng tin Tòa B1 - KTX Khu B',
     'SOLD', 342, 47, 'ban', 'ktx', 'thu'),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ',
     'Xe đạp địa hình khung nhôm siêu nhẹ, 21 tốc độ Shimano, phanh đĩa trước sau. Đã thay săm lốp mới, tra dầu xích. Đi 2 năm vẫn rất chắc.',
     1200000, 2800000, FALSE, FALSE, 'GOOD_85', 'Mới 80% - Xe chắc chạy tốt',
     'Ra trường / Không dùng nữa', 'Sảnh KTX Khu B - Chỗ gửi xe',
     'SOLD', 421, 66, 'xe', 'ktx', 'thu'),
    ('Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính ĐHQG',
     'Trọn bộ giáo trình Giải tích 1, 2 và Đại số tuyến tính chuẩn ĐHQG. Tặng kèm tập đề thi mẫu 5 năm gần nhất.',
     35000, 95000, FALSE, FALSE, 'LIKE_NEW_95', 'Sách đẹp - Tặng kèm đề thi',
     'Học xong môn / Qua môn', 'Thư viện Trung tâm ĐH Tự Nhiên',
     'SOLD', 233, 39, 'sach', 'tn', 'anh'),

    -- ============ IN_TRANSACTION (1) — đang giao dịch ============
    ('Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB',
     'Laptop học tập, code, thiết kế nhẹ. Pin còn 4-5 tiếng. Bàn phím đủ numpad, màn hình FHD viền mỏng. Bán vì lên đời MacBook.',
     5500000, 12000000, FALSE, TRUE, 'GOOD_85', 'Mới 85% - Còn bảo hành hãng',
     'Lên đời máy mới', 'Sảnh tòa B - KTX Khu B',
     'IN_TRANSACTION', 618, 95, 'pc', 'ktx', 'minh'),

    -- ============ HIDDEN (1) — admin đã ẩn ============
    ('Quạt mini cầm tay sạc USB (tin trùng nội dung)',
     'Quạt mini cầm tay sạc USB, 3 tốc độ. Tin bị ẩn do trùng với tin khác của cùng người bán.',
     45000, 120000, FALSE, FALSE, 'USABLE', 'Còn dùng tốt',
     'Pass đồ không dùng', 'KTX Khu B - Tòa B4',
     'HIDDEN', 12, 1, 'dien', 'ktx', 'tuan')
) AS p(title, description, price, original_price, is_free, is_urgent, item_condition, condition_note, pass_reason, pickup_spot, status, view_count, favorite_count, cat_key, uni_key, seller_key)
WHERE NOT EXISTS (SELECT 1 FROM products existing WHERE existing.title = p.title);

-- =====================
-- 6. PRODUCT IMAGES (2 ảnh cho mỗi tin, JPEG)
-- =====================
INSERT INTO product_images (product_id, image_url)
SELECT p.id, img.url
FROM products p
JOIN (VALUES
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm',                    'https://images.unsplash.com/photo-1618941716939-553df3c6c278?w=800&auto=format&fit=crop&q=80'),
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm',                    'https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=800&auto=format&fit=crop&q=80'),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox',             'https://images.unsplash.com/photo-1585515320310-259814833e62?w=800&auto=format&fit=crop&q=80'),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox',             'https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?w=800&auto=format&fit=crop&q=80'),
    ('Đèn học chống cận LED 3 chế độ ánh sáng',                      'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&auto=format&fit=crop&q=80'),
    ('Kệ nhựa đầu giường 3 tầng có bánh xe',                         'https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?w=800&auto=format&fit=crop&q=80'),
    ('Ghế xoay văn phòng lưng lưới chân sao',                        'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=800&auto=format&fit=crop&q=80'),
    ('Ghế xoay văn phòng lưng lưới chân sao',                        'https://images.unsplash.com/photo-1503602642458-232111445657?w=800&auto=format&fit=crop&q=80'),
    ('Áo khoác gió 2 lớp chống nước size L',                         'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&auto=format&fit=crop&q=80'),
    ('Giày sneaker trắng size 41 chính hãng',                        'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&auto=format&fit=crop&q=80'),
    ('Giày sneaker trắng size 41 chính hãng',                        'https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=800&auto=format&fit=crop&q=80'),
    ('Máy tính Casio fx-570VN Plus kèm bao da',                      'https://images.unsplash.com/photo-1587145820266-a5951ee6f620?w=800&auto=format&fit=crop&q=80'),
    ('Giáo trình Lập trình Java & Cấu trúc dữ liệu',                 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=800&auto=format&fit=crop&q=80'),
    ('Giáo trình Lập trình Java & Cấu trúc dữ liệu',                 'https://images.unsplash.com/photo-1512820790803-83ca734da794?w=800&auto=format&fit=crop&q=80'),
    ('Chuột không dây Logitech M331 Silent',                         'https://images.unsplash.com/photo-1527814050087-3793815479db?w=800&auto=format&fit=crop&q=80'),
    ('Màn hình Dell 24 inch FHD viền mỏng',                          'https://images.unsplash.com/photo-1541140532154-b024d705b90a?w=800&auto=format&fit=crop&q=80'),
    ('Vợt cầu lông Yonex chính hãng kèm bao',                        'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=800&auto=format&fit=crop&q=80'),
    ('Bộ tạ tay 2x5kg kèm thanh đòn',                                'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?w=800&auto=format&fit=crop&q=80'),
    ('Bộ tạ tay 2x5kg kèm thanh đòn',                                'https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=800&auto=format&fit=crop&q=80'),
    ('Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm',                   'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=800&auto=format&fit=crop&q=80'),
    ('Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm',                   'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?w=800&auto=format&fit=crop&q=80'),
    ('Máy tính bỏ túi Casio fx-580VN X chính hãng Bitex',            'https://images.unsplash.com/photo-1587145820266-a5951ee6f620?w=800&auto=format&fit=crop&q=80'),
    ('Bàn phím cơ không dây Keychron K6 Red Switch',                 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=800&auto=format&fit=crop&q=80'),
    ('Tai nghe Sony WH-1000XM4 chống ồn ANC',                        'https://images.unsplash.com/photo-1583394838336-acd977736f90?w=800&auto=format&fit=crop&q=80'),
    ('Giáo trình Kinh tế Vi mô & Vĩ mô bản dịch tiếng Việt',          'https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=800&auto=format&fit=crop&q=80'),
    ('Giáo trình Kinh tế Vi mô & Vĩ mô bản dịch tiếng Việt',          'https://images.unsplash.com/photo-1495446815901-a7297e633e8d?w=800&auto=format&fit=crop&q=80'),
    ('Tủ lạnh mini 50L cho phòng trọ',                               'https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=800&auto=format&fit=crop&q=80'),
    ('Bàn học gấp gọn chân sắt 80x40cm',                             'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?w=800&auto=format&fit=crop&q=80'),
    ('Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh',        'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?w=800&auto=format&fit=crop&q=80'),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ',           'https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=800&auto=format&fit=crop&q=80'),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ',           'https://images.unsplash.com/photo-1571068316344-75bc76f77890?w=800&auto=format&fit=crop&q=80'),
    ('Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính ĐHQG',     'https://images.unsplash.com/photo-1519682337058-a94d519337bc?w=800&auto=format&fit=crop&q=80'),
    ('Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB',        'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=800&auto=format&fit=crop&q=80'),
    ('Quạt mini cầm tay sạc USB (tin trùng nội dung)',               'https://images.unsplash.com/photo-1618941716939-553df3c6c278?w=800&auto=format&fit=crop&q=80')
) AS img(title, url) ON p.title = img.title
WHERE NOT EXISTS (
    SELECT 1 FROM product_images pi
    WHERE pi.product_id = p.id AND pi.image_url = img.url
);

-- =====================
-- 7. ORDER REQUESTS MẪU (8 đơn, đủ 6 trạng thái)
-- =====================
INSERT INTO order_requests (
    product_id, buyer_id, seller_id, offered_price, meetup_location, meetup_time, buyer_note,
    status, platform_fee_rate, platform_fee_amount, final_seller_amount, final_buyer_amount,
    fee_payer, payment_status, completed_at, confirmed_at, cancel_reason, dispute_reason
)
SELECT
    p.id, b.id, p.seller_id, o.price, o.spot, o.meet_time, o.note,
    o.status, o.rate, o.fee, o.price - o.fee, o.price,
    'SELLER',
    CASE WHEN o.status = 'COMPLETED' THEN 'COMPLETED' ELSE 'PENDING' END,
    CASE WHEN o.status = 'COMPLETED' THEN CURRENT_TIMESTAMP - INTERVAL '3 day' ELSE NULL END,
    CASE WHEN o.status IN ('ACCEPTED','COMPLETED') THEN CURRENT_TIMESTAMP - INTERVAL '5 day' ELSE NULL END,
    CASE WHEN o.status IN ('REJECTED','CANCELLED') THEN o.note ELSE NULL END,
    CASE WHEN o.status = 'DISPUTED' THEN o.note ELSE NULL END
FROM (VALUES
    ('Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh', 'ngocanh@hcmus.edu.vn', 110000,
     'Sảnh Tầng 1 Tòa B1 - KTX Khu B', '17:00 chiều nay', 'Chiều 17h mình xuống sảnh B1 nhận bàn nhé!',
     'COMPLETED', 0.0400::NUMERIC, 4000::NUMERIC),
    ('Xe đạp thể thao Totem 26 inch khung nhôm 21 tốc độ', 'minhkhang@hcmut.edu.vn', 1200000,
     'Sảnh KTX Khu B - Chỗ gửi xe', '9:00 sáng thứ 7', 'Mình xem xe trước rồi chuyển khoản sau nhé.',
     'COMPLETED', 0.0500::NUMERIC, 50000::NUMERIC),
    ('Combo Giáo trình Giải tích 1 & 2 + Đại số Tuyến tính ĐHQG', 'mailinh@hcmus.edu.vn', 35000,
     'Thư viện Trung tâm ĐH Tự Nhiên', '15:30 hôm nay', 'Bạn cho mình xem mục lục trước nhé.',
     'COMPLETED', 0.0300::NUMERIC, 2000::NUMERIC),
    ('Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB', 'thutrang@hcmut.edu.vn', 5500000,
     'Sảnh tòa B - KTX Khu B', '19:00 tối nay', 'Mình cần test pin và bàn phím kỹ ạ.',
     'ACCEPTED', 0.0500::NUMERIC, 50000::NUMERIC),
    ('Tai nghe Sony WH-1000XM4 chống ồn ANC', 'quanghuy@uit.edu.vn', 3200000,
     'Thư viện ĐH Tự Nhiên', '13:00 thứ 5', 'Mình muốn thử chống ồn trước khi lấy.',
     'PENDING', 0.0500::NUMERIC, 50000::NUMERIC),
    ('Bàn phím cơ không dây Keychron K6 Red Switch', 'dinhduy@hcmus.edu.vn', 550000,
     'Sảnh Tòa A - ĐH CNTT (UIT)', '18:30 hôm nay', 'Còn bảo hành không bạn?',
     'PENDING', 0.0400::NUMERIC, 22000::NUMERIC),
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm', 'thanhtruc@uel.edu.vn', 120000,
     'Sảnh Tòa H1 - ĐH Bách Khoa', '16:00 hôm qua', 'Mình qua lấy quạt nhé.',
     'REJECTED', 0.0400::NUMERIC, 5000::NUMERIC),
    ('Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox', 'tuananh@vnuhcm.edu.vn', 110000,
     'Sảnh Tòa A - ĐH CNTT (UIT)', '20:00 hôm qua', 'Nồi bị lỗi nút bấm, mình muốn trả hàng.',
     'DISPUTED', 0.0400::NUMERIC, 4000::NUMERIC)
) AS o(title, buyer_email, price, spot, meet_time, note, status, rate, fee)
JOIN products p ON p.title = o.title
JOIN users b ON b.email = o.buyer_email
WHERE NOT EXISTS (
    SELECT 1 FROM order_requests x
    WHERE x.product_id = p.id AND x.buyer_id = b.id
);

-- Sinh mã đơn hiển thị cho các đơn chưa có mã
UPDATE order_requests
SET order_code = 'PD' || LPAD(id::TEXT, 6, '0')
WHERE order_code IS NULL;

-- =====================
-- 8. TRANSACTIONS (từ các đơn COMPLETED)
-- =====================
INSERT INTO transactions (order_id, buyer_id, seller_id, product_id, transaction_amount, platform_fee_rate, platform_fee_amount, seller_receive_amount)
SELECT
    o.id, o.buyer_id, o.seller_id, o.product_id, o.offered_price,
    o.platform_fee_rate, o.platform_fee_amount, o.final_seller_amount
FROM order_requests o
WHERE o.status = 'COMPLETED'
  AND o.payment_status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.order_id = o.id);

-- =====================
-- 9. REVIEWS (đánh giá cho đơn đã hoàn tất)
-- =====================
INSERT INTO reviews (order_id, reviewer_id, reviewed_user_id, rating, comment)
SELECT o.id, o.buyer_id, o.seller_id,
       CASE WHEN o.offered_price > 1000000 THEN 5 ELSE 4 END,
       CASE
           WHEN o.offered_price > 1000000 THEN 'Máy đẹp đúng mô tả, bạn bán nhiệt tình, giao dịch nhanh.'
           WHEN o.offered_price > 100000  THEN 'Đồ ổn trong tầm giá, đóng gói cẩn thận. Cảm ơn bạn!'
           ELSE 'Sách còn tốt, đúng hẹn. Sẽ ủng hộ tiếp.'
       END
FROM order_requests o
WHERE o.status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_id = o.id);

-- =====================
-- 10. WISHLISTS MẪU (nhiều người, nhiều món)
-- =====================
INSERT INTO wishlists (user_id, product_id)
SELECT u.id, p.id
FROM (VALUES
    ('minhtu.nguyen@vnuhcm.edu.vn', 'Màn hình Dell 24 inch FHD viền mỏng'),
    ('minhtu.nguyen@vnuhcm.edu.vn', 'Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm'),
    ('hoangphuc@uit.edu.vn',        'Màn hình Dell 24 inch FHD viền mỏng'),
    ('hoangphuc@uit.edu.vn',        'Ghế xoay văn phòng lưng lưới chân sao'),
    ('ngocanh@hcmus.edu.vn',        'Đèn học chống cận LED 3 chế độ ánh sáng'),
    ('ngocanh@hcmus.edu.vn',        'Giày sneaker trắng size 41 chính hãng'),
    ('ducminh@uel.edu.vn',          'Tủ lạnh mini 50L cho phòng trọ'),
    ('thutrang@hcmut.edu.vn',       'Áo khoác gió 2 lớp chống nước size L'),
    ('quanghuy@uit.edu.vn',         'Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm'),
    ('mailinh@hcmus.edu.vn',        'Giáo trình Lập trình Java & Cấu trúc dữ liệu'),
    ('khanhlinh@vnuhcm.edu.vn',     'Kệ nhựa đầu giường 3 tầng có bánh xe'),
    ('minhkhang@hcmut.edu.vn',      'Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm'),
    ('thaovy@uit.edu.vn',           'Ghế xoay văn phòng lưng lưới chân sao'),
    ('tuananh@vnuhcm.edu.vn',       'Quạt đứng Senko lồng sắt 3 tốc độ chạy êm')
) AS w(email, title)
JOIN users u ON u.email = w.email
JOIN products p ON p.title = w.title
ON CONFLICT (user_id, product_id) DO NOTHING;

-- =====================
-- 11. CHAT MESSAGES MẪU
-- =====================
INSERT INTO chat_messages (sender_id, receiver_id, product_id, content, message_type, offered_price, meetup_location, meetup_time, is_read)
SELECT s.id, r.id, p.id, m.content, 'TEXT', m.offered_price, m.spot, m.meet_time, m.is_read
FROM (VALUES
    ('ngocanh@hcmus.edu.vn', 'minhtu.nguyen@vnuhcm.edu.vn', 'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh',
     'Bàn còn không bạn? Mình lấy luôn hôm nay được không?', NULL::NUMERIC, 'Sảnh B1', '17:00', TRUE),
    ('minhtu.nguyen@vnuhcm.edu.vn', 'ngocanh@hcmus.edu.vn', 'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh',
     'Còn nha, 17h mình có mặt ở sảnh B1.', NULL::NUMERIC, 'Sảnh B1', '17:00', TRUE),
    ('quanghuy@uit.edu.vn', 'phuc.tran@uit.edu.vn', 'Tai nghe Sony WH-1000XM4 chống ồn ANC',
     'Tai nghe còn bảo hành không ạ? Mình trả 3 triệu được không?', 3000000::NUMERIC, 'Sảnh tòa A', '13:00', TRUE),
    ('phuc.tran@uit.edu.vn', 'quanghuy@uit.edu.vn', 'Tai nghe Sony WH-1000XM4 chống ồn ANC',
     'Hết bảo hành rồi nhưng còn rất tốt. 3.1 triệu mình giao tận nơi.', 3100000::NUMERIC, 'Sảnh tòa A', '13:00', FALSE),
    ('dinhduy@hcmus.edu.vn', 'phuc.tran@uit.edu.vn', 'Bàn phím cơ không dây Keychron K6 Red Switch',
     'Keycap zin còn đủ không bạn?', NULL::NUMERIC, 'Sảnh tòa A', '18:30', FALSE),
    ('thutrang@hcmut.edu.vn', 'ducminh@uel.edu.vn', 'Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB',
     'Mình muốn test pin bằng phần mềm được không?', NULL::NUMERIC, 'Sảnh tòa B', '19:00', TRUE),
    ('ducminh@uel.edu.vn', 'thutrang@hcmut.edu.vn', 'Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB',
     'Được nhé, bạn mang USB theo test thoải mái.', NULL::NUMERIC, 'Sảnh tòa B', '19:00', TRUE),
    ('tuananh@vnuhcm.edu.vn', 'phuc.tran@uit.edu.vn', 'Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox',
     'Nút bấm bị kẹt, mình muốn trả hàng và nhận lại tiền.', NULL::NUMERIC, 'Sảnh tòa A', '20:00', FALSE),
    ('mailinh@hcmus.edu.vn', 'thaovy@uit.edu.vn', 'Giáo trình Lập trình Java & Cấu trúc dữ liệu',
     'Sách còn ghi chú nhiều không bạn?', NULL::NUMERIC, 'Sảnh tòa A', '15:00', TRUE),
    ('thaovy@uit.edu.vn', 'mailinh@hcmus.edu.vn', 'Giáo trình Lập trình Java & Cấu trúc dữ liệu',
     'Chỉ vài trang có bút chì thôi, mình gửi ảnh cho bạn xem nhé.', NULL::NUMERIC, 'Sảnh tòa A', '15:00', TRUE)
) AS m(sender_email, receiver_email, title, content, offered_price, spot, meet_time, is_read)
JOIN users s ON s.email = m.sender_email
JOIN users r ON r.email = m.receiver_email
JOIN products p ON p.title = m.title
WHERE NOT EXISTS (
    SELECT 1 FROM chat_messages c
    WHERE c.sender_id = s.id AND c.receiver_id = r.id AND c.content = m.content
);

-- =====================
-- 12. NOTIFICATIONS MẪU
-- =====================
INSERT INTO notifications (user_id, type, title, message, reference_id, is_read)
SELECT u.id, n.type, n.title, n.message,
       (SELECT p.id FROM products p WHERE p.title = n.ref_title LIMIT 1),
       n.is_read
FROM (VALUES
    ('hoangyen@hcmut.edu.vn', 'PRODUCT_APPROVED', 'Tin đăng đã được duyệt 🎉',
     'Tin "Quạt đứng Senko lồng sắt 3 tốc độ chạy êm" đã được duyệt và hiển thị trên sàn.',
     'Quạt đứng Senko lồng sắt 3 tốc độ chạy êm', TRUE),
    ('phuc.tran@uit.edu.vn', 'PRODUCT_PENDING', 'Tin đăng đang chờ duyệt',
     'Tin "Bàn phím cơ không dây Keychron K6 Red Switch" đang chờ admin kiểm duyệt.',
     'Bàn phím cơ không dây Keychron K6 Red Switch', FALSE),
    ('minhtu.nguyen@vnuhcm.edu.vn', 'ORDER_RECEIVED', 'Bạn có yêu cầu đặt mua mới',
     'Có người muốn mua "Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh".',
     'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh', TRUE),
    ('ngocanh@hcmus.edu.vn', 'ORDER_COMPLETED', 'Đơn hàng đã hoàn tất',
     'Đơn mua "Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh" đã hoàn tất. Hãy đánh giá người bán nhé!',
     'Bộ bàn ghế học gấp gọn KTX kèm giá đỡ sách thông minh', FALSE),
    ('ducminh@uel.edu.vn', 'ORDER_ACCEPTED', 'Người bán đã nhận đơn',
     'Đơn mua laptop Dell Inspiron 3511 của bạn đã được người bán chấp nhận.',
     'Laptop Dell Inspiron 3511 i5-1135G7 RAM 8GB SSD 256GB', FALSE),
    ('tuananh@vnuhcm.edu.vn', 'ORDER_DISPUTED', 'Đơn hàng đang tranh chấp',
     'Đơn mua nồi lẩu mini Bear đang được admin xem xét khiếu nại.',
     'Nồi lẩu mini Bear 1.2L đa năng kèm xửng hấp inox', FALSE),
    ('thaovy@uit.edu.vn', 'PRODUCT_APPROVED', 'Tin đăng đã được duyệt 🎉',
     'Tin "Giáo trình Lập trình Java & Cấu trúc dữ liệu" đã được duyệt.',
     'Giáo trình Lập trình Java & Cấu trúc dữ liệu', TRUE),
    ('tuananh@vnuhcm.edu.vn', 'PRODUCT_HIDDEN', 'Tin đăng bị ẩn',
     'Tin "Quạt mini cầm tay sạc USB (tin trùng nội dung)" đã bị ẩn do trùng nội dung.',
     'Quạt mini cầm tay sạc USB (tin trùng nội dung)', TRUE)
) AS n(email, type, title, message, ref_title, is_read)
JOIN users u ON u.email = n.email
WHERE NOT EXISTS (
    SELECT 1 FROM notifications x
    WHERE x.user_id = u.id AND x.title = n.title AND x.message = n.message
);

-- =====================
-- 13. MODERATION LOGS MẪU (audit admin duyệt / ẩn bài)
-- =====================
INSERT INTO moderation_logs (product_id, admin_id, action, from_status, to_status, reason, created_at)
SELECT p.id, a.id, m.action, m.from_status, m.to_status, m.reason,
       CURRENT_TIMESTAMP - ((m.days_ago::TEXT) || ' day')::INTERVAL
FROM (VALUES
    ('Quạt đứng Senko lồng sắt 3 tốc độ chạy êm', 'admin@chodocu.edu.vn', 'APPROVE', 'PENDING', 'APPROVED', 'Nội dung hợp lệ', 6),
    ('Ghế xoay văn phòng lưng lưới chân sao', 'admin@chodocu.edu.vn', 'APPROVE', 'PENDING', 'APPROVED', 'Ảnh rõ ràng, giá hợp lý', 5),
    ('Máy ảnh Fujifilm X-A5 kèm lens kit 15-45mm', 'mod@passuni.vn', 'APPROVE', 'PENDING', 'APPROVED', 'Đã kiểm tra mô tả', 4),
    ('Quạt mini cầm tay sạc USB (tin trùng nội dung)', 'admin@chodocu.edu.vn', 'HIDE', 'APPROVED', 'HIDDEN', 'Trùng nội dung với tin khác của cùng người bán', 2)
) AS m(title, admin_email, action, from_status, to_status, reason, days_ago)
JOIN products p ON p.title = m.title
JOIN users a ON a.email = m.admin_email
WHERE NOT EXISTS (
    SELECT 1 FROM moderation_logs l
    WHERE l.product_id = p.id AND l.action = m.action
);

-- =====================
-- VERIFICATION
-- =====================
SELECT 'universities' as table_name, COUNT(*) as count FROM universities
UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'products_approved', COUNT(*) FROM products WHERE status = 'APPROVED'
UNION ALL SELECT 'product_images', COUNT(*) FROM product_images
UNION ALL SELECT 'orders', COUNT(*) FROM order_requests
UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL SELECT 'reviews', COUNT(*) FROM reviews
UNION ALL SELECT 'wishlists', COUNT(*) FROM wishlists
UNION ALL SELECT 'chat_messages', COUNT(*) FROM chat_messages
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
UNION ALL SELECT 'moderation_logs', COUNT(*) FROM moderation_logs;
