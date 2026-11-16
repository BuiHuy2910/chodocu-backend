-- =================================================================================
-- CHỢ ĐỒ CŨ — CHẾ ĐỘ APP GỌI THẲNG SUPABASE (KHÔNG CẦN CHẠY BACKEND) — BẢN 2
--
-- Thứ tự chạy trong Supabase SQL Editor (hoặc psql), mỗi file chạy lại bao nhiêu lần cũng được:
--   1. database_schema.sql
--   2. seed_data.sql              (tuỳ chọn — dữ liệu mẫu)
--   3. supabase_direct_setup.sql  (file này)
--
-- Nguyên tắc:
--   * App chỉ có anon key -> KHÔNG đọc / ghi trực tiếp bảng gốc (trừ danh mục và trường).
--   * Mọi thao tác đi qua hàm api_* (SECURITY DEFINER), xác thực bằng mã phiên
--     trong bảng app_sessions (cấp khi đăng nhập / đăng ký, hết hạn sau 30 ngày).
--   * Mọi hàm api_* trả về jsonb, khoá JSON dạng snake_case.
--   * Lỗi nghiệp vụ: message là câu tiếng Việt cho người dùng, hint là mã lỗi để app xử lý:
--     INVALID_SESSION, WRONG_CREDENTIALS, ACCOUNT_LOCKED, EMAIL_EXISTS, VALIDATION,
--     NOT_FOUND, FORBIDDEN, CONFLICT.
--   * Cột thời gian trong DB lưu giờ UTC; JSON trả ra dạng ISO-8601 có hậu tố Z.
--     Riêng giờ hẹn (meetup_time) là chuỗi "yyyy-MM-ddTHH:mm" theo giờ Việt Nam.
--   * Các hàm bắt đầu bằng dấu gạch dưới (_xxx) là hàm nội bộ, app không gọi được.
-- =================================================================================

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;   -- băm mật khẩu (crypt, gen_salt)
create extension if not exists unaccent with schema extensions;   -- tìm kiếm không dấu

-- =================================================================================
-- 1. BỔ SUNG CẤU TRÚC (an toàn khi chạy lại)
-- =================================================================================
alter table chat_messages add column if not exists image_url varchar(1000);
alter table product_images add column if not exists id bigserial primary key;

create table if not exists app_sessions (
    token uuid primary key default gen_random_uuid(),
    user_id bigint not null references users(id) on delete cascade,
    created_at timestamptz not null default now()
);
create index if not exists idx_app_sessions_user on app_sessions(user_id);

-- =================================================================================
-- 2. DỌN BẢN CŨ: xoá mọi hàm api_* / hàm nội bộ cũ (kể cả bản khác tham số) và view cũ
-- =================================================================================
do $$
declare
    f record;
begin
    for f in
        select p.oid::regprocedure as signature
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and (p.proname like 'api\_%' or p.proname like '\_%')
          -- không đụng tới hàm của extension
          and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
    loop
        execute 'drop function if exists ' || f.signature || ' cascade';
    end loop;
end $$;

drop view if exists v_chat_messages, v_orders, v_user_profiles, v_public_products, v_products cascade;

-- =================================================================================
-- 3. HÀM NỘI BỘ DÙNG CHUNG
-- =================================================================================

-- Báo lỗi nghiệp vụ: message cho người dùng, hint là mã lỗi cho app
create or replace function _fail(p_hint text, p_message text)
returns void
language plpgsql
set search_path = public, extensions
as $$
begin
    raise exception using errcode = 'P0001', message = p_message, hint = p_hint;
end;
$$;

-- Giờ hiện tại theo UTC (mọi cột thời gian trong DB lưu giờ UTC)
create or replace function _utc_now()
returns timestamp
language sql stable
set search_path = public, extensions
as $$ select (now() at time zone 'UTC')::timestamp $$;

-- Thời gian dạng ISO-8601 có hậu tố Z để trả về trong JSON
create or replace function _iso(p_time timestamp)
returns text
language sql stable
set search_path = public, extensions
as $$ select to_char(p_time, 'YYYY-MM-DD"T"HH24:MI:SS"Z"') $$;

-- Đọc một cấu hình của sàn trong bảng platform_settings
create or replace function _setting(p_key text)
returns text
language sql stable
set search_path = public, extensions
as $$ select nullif(trim(value), '') from platform_settings where setting_key = p_key $$;

-- Cấu hình dạng true / false (giống Boolean.parseBoolean bên Java)
create or replace function _setting_bool(p_key text, p_default boolean)
returns boolean
language sql stable
set search_path = public, extensions
as $$ select coalesce(lower(_setting(p_key)) = 'true', p_default) $$;

-- Số tiền dạng "150.000 đ"; 0 đồng thì ghi "Miễn phí" (dùng trong tin nhắn / thông báo)
create or replace function _price_text(p_amount numeric)
returns text
language sql stable
set search_path = public, extensions
as $$
    select case
        when coalesce(p_amount, 0) = 0 then 'Miễn phí'
        else replace(to_char(p_amount, 'FM999,999,999,990'), ',', '.') || ' đ'
    end
$$;

-- Danh sách điểm hẹn chuẩn của một trường (tách chuỗi "Sảnh A, Thư viện B")
create or replace function _university_spots(p_university_id bigint)
returns text[]
language sql stable
set search_path = public, extensions
as $$
    select coalesce(array_agg(trim(spot)) filter (where trim(spot) <> ''), '{}')
    from universities u
    cross join unnest(string_to_array(u.safe_meetup_spots, ',')) as spot
    where u.id = p_university_id
$$;

-- Kiểm tra giờ hẹn "yyyy-MM-ddTHH:mm" (giờ Việt Nam): phải ở tương lai và trong vòng 30 ngày
create or replace function _check_meetup_time(p_value text)
returns timestamp
language plpgsql stable
set search_path = public, extensions
as $$
declare
    v_time timestamp;
    v_now  timestamp := (now() at time zone 'Asia/Ho_Chi_Minh')::timestamp;
begin
    if p_value is null or trim(p_value) !~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$' then
        perform _fail('VALIDATION', 'Vui lòng chọn ngày và giờ hẹn gặp.');
    end if;
    begin
        v_time := trim(p_value)::timestamp;
    exception when others then
        perform _fail('VALIDATION', 'Ngày giờ hẹn không hợp lệ.');
    end;
    if v_time <= v_now then
        perform _fail('VALIDATION', 'Giờ hẹn phải sau thời điểm hiện tại.');
    end if;
    if v_time > v_now + interval '30 days' then
        perform _fail('VALIDATION', 'Chỉ được hẹn trong vòng 30 ngày tới.');
    end if;
    return v_time;
end;
$$;

-- "2026-11-17T09:30" -> "09:30 ngày 17/11/2026" để ghi vào tin nhắn / thông báo
create or replace function _meetup_label(p_value text)
returns text
language plpgsql stable
set search_path = public, extensions
as $$
begin
    return to_char(p_value::timestamp, 'HH24:MI "ngày" DD/MM/YYYY');
exception when others then
    return coalesce(p_value, '');
end;
$$;

-- So khớp mật khẩu với chuỗi băm BCrypt (dùng chung định dạng với Spring Security)
create or replace function _password_matches(p_password text, p_hash text)
returns boolean
language plpgsql stable
set search_path = public, extensions
as $$
begin
    if p_password is null or p_hash is null then
        return false;
    end if;
    return p_hash = crypt(p_password, p_hash);
exception when others then
    return false;   -- chuỗi băm hỏng: coi như sai mật khẩu
end;
$$;

-- Trạng thái ban đầu của tin mới / tin vừa sửa: chờ duyệt, trừ khi admin tắt kiểm duyệt
create or replace function _initial_product_status()
returns text
language sql stable
set search_path = public, extensions
as $$ select case when _setting_bool('post_require_approval', true) then 'PENDING' else 'APPROVED' end $$;

-- =================================================================================
-- 4. PHIÊN ĐĂNG NHẬP
-- =================================================================================

-- Id người dùng của mã phiên. Báo INVALID_SESSION nếu mã sai, quá 30 ngày hoặc tài khoản bị khoá.
create or replace function _session_user(p_token text)
returns bigint
language plpgsql
set search_path = public, extensions
as $$
declare
    v_token   uuid;
    v_user_id bigint;
begin
    begin
        v_token := trim(p_token)::uuid;
    exception when others then
        v_token := null;    -- không đúng định dạng uuid
    end;

    if v_token is not null then
        select s.user_id into v_user_id
        from app_sessions s
        join users u on u.id = s.user_id
        where s.token = v_token
          and s.created_at > now() - interval '30 days'
          and coalesce(u.active, true);
    end if;

    if v_user_id is null then
        perform _fail('INVALID_SESSION', 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    end if;
    return v_user_id;
end;
$$;

-- Như _session_user nhưng cho phép chưa đăng nhập (không có mã phiên thì trả về null)
create or replace function _session_user_optional(p_token text)
returns bigint
language plpgsql
set search_path = public, extensions
as $$
begin
    if p_token is null or trim(p_token) = '' then
        return null;
    end if;
    return _session_user(p_token);
end;
$$;

-- Tạo phiên mới cho người dùng, trả về mã phiên
create or replace function _new_session(p_user_id bigint)
returns text
language plpgsql
set search_path = public, extensions
as $$
declare
    v_token uuid;
begin
    -- Dọn các phiên đã hết hạn của người này
    delete from app_sessions where user_id = p_user_id and created_at <= now() - interval '30 days';
    insert into app_sessions (user_id) values (p_user_id) returning token into v_token;
    return v_token::text;
end;
$$;

-- =================================================================================
-- 5. DỰNG JSON TRẢ VỀ CHO APP
-- =================================================================================

-- <user>: hồ sơ đầy đủ của người đang đăng nhập
create or replace function _user_json(p_user_id bigint)
returns jsonb
language sql stable
set search_path = public, extensions
as $$
    select jsonb_build_object(
        'id', u.id,
        'email', u.email,
        'full_name', u.full_name,
        'phone', u.phone,
        'avatar_url', u.avatar_url,
        'bio', u.bio,
        'dorm_area', u.dorm_area,
        'role', u.role,
        'active', coalesce(u.active, true),
        'university_id', u.university_id,
        'university_name', un.name,
        'university_short_name', un.short_name,
        'rating', u.rating,
        'completed_deals', coalesce(u.completed_deals, 0),
        'created_at', _iso(u.created_at)
    )
    from users u
    left join universities un on un.id = u.university_id
    where u.id = p_user_id
$$;

-- Ảnh đầu tiên (ảnh bìa) của một tin
create or replace function _cover_image(p_product_id bigint)
returns text
language sql stable
set search_path = public, extensions
as $$ select image_url from product_images where product_id = p_product_id order by id limit 1 $$;

-- <product>: một tin đăng, kèm người bán và danh sách ảnh theo đúng thứ tự
create or replace function _product_json(p_product_id bigint, p_viewer_id bigint)
returns jsonb
language sql stable
set search_path = public, extensions
as $$
    select jsonb_build_object(
        'id', p.id,
        'title', p.title,
        'description', p.description,
        'price', p.price,
        'original_price', p.original_price,
        'is_free', coalesce(p.is_free, false),
        'is_urgent', coalesce(p.is_urgent, false),
        'item_condition', p.item_condition,
        'condition_note', p.condition_note,
        'pass_reason', p.pass_reason,
        'pickup_spot', p.pickup_spot,
        'status', p.status,
        'reject_reason', p.reject_reason,
        'view_count', coalesce(p.view_count, 0),
        'favorite_count', coalesce(p.favorite_count, 0),
        'is_favorited', exists (
            select 1 from wishlists w where w.product_id = p.id and w.user_id = p_viewer_id),
        'created_at', _iso(p.created_at),
        'updated_at', _iso(p.updated_at),
        'category_id', c.id,
        'category_name', c.name,
        'category_icon', c.icon,
        'university_id', un.id,
        'university_name', un.name,
        'university_short_name', un.short_name,
        'seller_id', s.id,
        'seller_name', s.full_name,
        'seller_avatar_url', s.avatar_url,
        'seller_rating', s.rating,
        'seller_completed_deals', coalesce(s.completed_deals, 0),
        'seller_university_short_name', su.short_name,
        'image_urls', coalesce(
            (select jsonb_agg(i.image_url order by i.id) from product_images i where i.product_id = p.id),
            '[]'::jsonb)
    )
    from products p
    join users s on s.id = p.seller_id
    left join categories c on c.id = p.category_id
    left join universities un on un.id = p.university_id
    left join universities su on su.id = s.university_id
    where p.id = p_product_id
$$;

-- <order>: một đơn đặt mua. Số điện thoại chỉ hiện khi đơn đã chốt hẹn (để liên hệ khi gặp).
create or replace function _order_json(p_order_id bigint)
returns jsonb
language sql stable
set search_path = public, extensions
as $$
    select jsonb_build_object(
        'id', o.id,
        'order_code', o.order_code,
        'status', o.status,
        'offered_price', o.offered_price,
        'meetup_location', o.meetup_location,
        'meetup_time', o.meetup_time,
        'buyer_note', o.buyer_note,
        'seller_note', o.seller_note,
        'cancel_reason', o.cancel_reason,
        'dispute_reason', o.dispute_reason,
        'payment_status', o.payment_status,
        'fee_payer', o.fee_payer,
        'platform_fee_rate', o.platform_fee_rate,
        'platform_fee_amount', o.platform_fee_amount,
        'final_seller_amount', o.final_seller_amount,
        'final_buyer_amount', o.final_buyer_amount,
        'created_at', _iso(o.created_at),
        'updated_at', _iso(o.updated_at),
        'completed_at', _iso(o.completed_at),
        'buyer_id', b.id,
        'buyer_name', b.full_name,
        'buyer_avatar_url', b.avatar_url,
        'buyer_phone', case when o.status in ('ACCEPTED', 'COMPLETED', 'DISPUTED') then b.phone end,
        'seller_id', s.id,
        'seller_name', s.full_name,
        'seller_avatar_url', s.avatar_url,
        'seller_phone', case when o.status in ('ACCEPTED', 'COMPLETED', 'DISPUTED') then s.phone end,
        'product_id', p.id,
        'product_title', p.title,
        'product_price', p.price,
        'product_image', _cover_image(p.id),
        'product_status', p.status,
        'product_university_id', p.university_id
    )
    from order_requests o
    join users b on b.id = o.buyer_id
    join users s on s.id = o.seller_id
    join products p on p.id = o.product_id
    where o.id = p_order_id
$$;

-- <message>: một tin nhắn chat
create or replace function _message_json(p_message_id bigint)
returns jsonb
language sql stable
set search_path = public, extensions
as $$
    select jsonb_build_object(
        'id', m.id,
        'sender_id', s.id,
        'sender_name', s.full_name,
        'sender_avatar_url', s.avatar_url,
        'receiver_id', r.id,
        'receiver_name', r.full_name,
        'receiver_avatar_url', r.avatar_url,
        'product_id', p.id,
        'product_title', p.title,
        'product_image', _cover_image(p.id),
        'product_price', p.price,
        'message_type', coalesce(m.message_type, 'TEXT'),
        'content', m.content,
        'image_url', m.image_url,
        'offered_price', m.offered_price,
        'meetup_location', m.meetup_location,
        'meetup_time', m.meetup_time,
        'is_read', coalesce(m.is_read, false),
        'created_at', _iso(m.created_at)
    )
    from chat_messages m
    join users s on s.id = m.sender_id
    join users r on r.id = m.receiver_id
    left join products p on p.id = m.product_id
    where m.id = p_message_id
$$;

-- <fee>: tính phí sàn cho một số tiền, giống PlatformSettingService.calculate() bên backend Java.
-- Thứ tự: bậc phí khớp số tiền (priority cao nhất) -> % cơ bản -> làm tròn theo bước -> min / max.
create or replace function _calc_fee(p_amount numeric)
returns jsonb
language plpgsql stable
set search_path = public, extensions
as $$
declare
    v_amount  numeric := greatest(coalesce(p_amount, 0), 0);
    v_enabled boolean := _setting_bool('fee_enabled', true);
    v_payer   text    := upper(coalesce(_setting('fee_payer'), 'SELLER'));
    v_step    numeric := coalesce(_setting('fee_round_step')::numeric, 1000);
    v_min     numeric := coalesce(_setting('min_platform_fee')::numeric, 2000);
    v_max     numeric := coalesce(_setting('max_platform_fee')::numeric, 50000);
    v_tier    platform_fee_tiers%rowtype;
    v_rate    numeric;
    v_fee     numeric := 0;
    v_half    numeric;
    v_seller  numeric := v_amount;
    v_buyer   numeric := v_amount;
begin
    select * into v_tier
    from platform_fee_tiers t
    where t.active
      and v_amount >= t.min_amount
      and (t.max_amount is null or v_amount <= t.max_amount)
    order by t.priority desc
    limit 1;

    v_rate := coalesce(v_tier.fee_rate, _setting('platform_fee_rate')::numeric, 0.05);

    if v_enabled then
        v_fee := v_amount * v_rate;
        if v_step > 0 then
            v_fee := round(v_fee / v_step) * v_step;
        else
            v_fee := round(v_fee);
        end if;
        if v_fee < v_min then v_fee := v_min; end if;
        if v_max > 0 and v_fee > v_max then v_fee := v_max; end if;
        if v_fee > v_amount then v_fee := v_amount; end if;   -- không thu quá giá trị đơn
    end if;

    if v_payer = 'BUYER' then
        v_buyer := v_amount + v_fee;
    elsif v_payer = 'SPLIT' then
        v_half := round(v_fee / 2);
        v_seller := v_amount - v_half;
        v_buyer := v_amount + (v_fee - v_half);
    else
        v_seller := v_amount - v_fee;   -- mặc định người bán chịu phí
    end if;

    return jsonb_build_object(
        'order_amount', v_amount,
        'fee_enabled', v_enabled,
        'fee_rate', v_rate,
        'fee_rate_percent', trim_scale(v_rate * 100)::text,
        'platform_fee_amount', v_fee,
        'seller_receive_amount', v_seller,
        'buyer_pay_amount', v_buyer,
        'fee_payer', v_payer,
        'applied_tier_name', coalesce(v_tier.name, 'Tỷ lệ cơ bản'),
        'applied_tier_id', v_tier.id          -- chỉ dùng nội bộ khi tạo đơn
    );
end;
$$;

-- =================================================================================
-- 6. HÀM NỘI BỘ CHO TIN ĐĂNG, ĐƠN, CHAT, THÔNG BÁO
-- =================================================================================

-- Kiểm tra dữ liệu form đăng / sửa tin
create or replace function _validate_product(
    p_title text,
    p_description text,
    p_price numeric,
    p_original_price numeric,
    p_is_free boolean,
    p_condition text,
    p_condition_note text,
    p_pass_reason text,
    p_category_id bigint,
    p_university_id bigint,
    p_pickup_spot text,
    p_image_urls text[]
)
returns void
language plpgsql stable
set search_path = public, extensions
as $$
declare
    v_url text;
begin
    if char_length(trim(coalesce(p_title, ''))) not between 5 and 120 then
        perform _fail('VALIDATION', 'Tiêu đề phải từ 5 đến 120 ký tự.');
    end if;
    if char_length(coalesce(p_description, '')) > 3000 then
        perform _fail('VALIDATION', 'Mô tả tối đa 3000 ký tự.');
    end if;
    if not coalesce(p_is_free, false) and coalesce(p_price, 0) <= 0 then
        perform _fail('VALIDATION', 'Vui lòng nhập giá bán lớn hơn 0 hoặc chọn tặng miễn phí.');
    end if;
    if coalesce(p_price, 0) > 1000000000 or coalesce(p_original_price, 0) > 1000000000 then
        perform _fail('VALIDATION', 'Giá không được vượt quá 1 tỷ đồng.');
    end if;
    if p_original_price is not null and p_original_price < 0 then
        perform _fail('VALIDATION', 'Giá gốc không hợp lệ.');
    end if;
    if p_condition is null or p_condition not in ('NEW_100', 'LIKE_NEW_95', 'GOOD_85', 'USABLE') then
        perform _fail('VALIDATION', 'Vui lòng chọn tình trạng món đồ.');
    end if;
    if char_length(coalesce(p_condition_note, '')) > 255 or char_length(coalesce(p_pass_reason, '')) > 255 then
        perform _fail('VALIDATION', 'Ghi chú tình trạng và lý do pass tối đa 255 ký tự.');
    end if;
    if p_category_id is null or not exists (select 1 from categories where id = p_category_id) then
        perform _fail('VALIDATION', 'Vui lòng chọn danh mục.');
    end if;
    if p_university_id is null or not exists (select 1 from universities where id = p_university_id) then
        perform _fail('VALIDATION', 'Vui lòng chọn trường / khu vực giao dịch.');
    end if;
    -- Điểm giao phải do người dùng chọn trong danh sách điểm hẹn chuẩn của trường
    if p_pickup_spot is null or not (trim(p_pickup_spot) = any (_university_spots(p_university_id))) then
        perform _fail('VALIDATION', 'Vui lòng chọn điểm giao dịch trong danh sách điểm hẹn của trường.');
    end if;
    if coalesce(array_length(p_image_urls, 1), 0) not between 1 and 5 then
        perform _fail('VALIDATION', 'Vui lòng thêm từ 1 đến 5 ảnh thật của món đồ.');
    end if;
    foreach v_url in array p_image_urls loop
        if v_url is null or trim(v_url) !~* '^https?://' or char_length(v_url) > 1000 then
            perform _fail('VALIDATION', 'Đường dẫn ảnh không hợp lệ, vui lòng tải ảnh lên lại.');
        end if;
    end loop;
end;
$$;

-- Lưu lại danh sách ảnh của tin theo đúng thứ tự (ảnh đầu tiên là ảnh bìa)
create or replace function _save_product_images(p_product_id bigint, p_image_urls text[])
returns void
language plpgsql
set search_path = public, extensions
as $$
declare
    v_url text;
begin
    delete from product_images where product_id = p_product_id;
    foreach v_url in array p_image_urls loop
        insert into product_images (product_id, image_url) values (p_product_id, trim(v_url));
    end loop;
end;
$$;

-- Tạo thông báo cho một người
create or replace function _notify(
    p_user_id bigint, p_type text, p_title text, p_message text, p_reference_id bigint)
returns void
language sql
set search_path = public, extensions
as $$
    insert into notifications (user_id, type, title, message, reference_id, is_read, created_at)
    values (p_user_id, p_type, p_title, left(p_message, 1000), p_reference_id, false, _utc_now());
$$;

-- Gửi tin nhắn hệ thống (hiện ở giữa khung chat) giữa hai bên của một giao dịch
create or replace function _system_message(
    p_sender_id bigint, p_receiver_id bigint, p_product_id bigint, p_content text)
returns void
language sql
set search_path = public, extensions
as $$
    insert into chat_messages (sender_id, receiver_id, product_id, content, message_type, is_read, created_at)
    values (p_sender_id, p_receiver_id, p_product_id, left(p_content, 1000), 'SYSTEM', false, _utc_now());
$$;

-- Từ chối tự động mọi yêu cầu mua còn chờ của một tin (khi đã chốt với người khác / đã bán)
create or replace function _reject_pending_orders(
    p_product_id bigint, p_keep_order_id bigint, p_seller_id bigint, p_reason text)
returns void
language plpgsql
set search_path = public, extensions
as $$
declare
    v_order record;
    v_title text;
begin
    select title into v_title from products where id = p_product_id;
    for v_order in
        select id, buyer_id
        from order_requests
        where product_id = p_product_id
          and status = 'PENDING'
          and id is distinct from p_keep_order_id
        for update
    loop
        update order_requests
        set status = 'REJECTED', cancel_reason = p_reason, status_updated_by = p_seller_id, updated_at = _utc_now()
        where id = v_order.id;

        perform _system_message(p_seller_id, v_order.buyer_id, p_product_id,
            'Yêu cầu mua đã tự động bị từ chối. Lý do: ' || p_reason);
        perform _notify(v_order.buyer_id, 'ORDER_REJECTED', 'Yêu cầu mua bị từ chối',
            'Yêu cầu mua "' || v_title || '" đã bị từ chối. Lý do: ' || p_reason, v_order.id);
    end loop;
end;
$$;

-- =================================================================================
-- 7. API TÀI KHOẢN
-- =================================================================================

-- Đăng ký (không cần xác thực email sinh viên) -> tự đăng nhập luôn
create or replace function api_register(
    p_full_name text,
    p_email text,
    p_phone text,
    p_password text,
    p_university_id bigint
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_name    text := trim(coalesce(p_full_name, ''));
    v_email   text := lower(trim(coalesce(p_email, '')));
    v_phone   text := regexp_replace(coalesce(p_phone, ''), '[\s.-]', '', 'g');
    v_user_id bigint;
begin
    if char_length(v_name) not between 2 and 100 then
        perform _fail('VALIDATION', 'Họ tên phải từ 2 đến 100 ký tự.');
    end if;
    if v_email !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' or char_length(v_email) > 255 then
        perform _fail('VALIDATION', 'Email không hợp lệ.');
    end if;
    if v_phone !~ '^(0|\+84)[0-9]{9,10}$' then
        perform _fail('VALIDATION', 'Số điện thoại không hợp lệ (ví dụ 0912345678).');
    end if;
    if char_length(coalesce(p_password, '')) not between 6 and 72 then
        perform _fail('VALIDATION', 'Mật khẩu phải từ 6 đến 72 ký tự.');
    end if;
    if p_university_id is null or not exists (select 1 from universities where id = p_university_id) then
        perform _fail('VALIDATION', 'Vui lòng chọn trường của bạn.');
    end if;
    if exists (select 1 from users where lower(email) = v_email) then
        perform _fail('EMAIL_EXISTS', 'Email này đã được đăng ký. Bạn hãy đăng nhập hoặc dùng email khác.');
    end if;

    begin
        insert into users (email, password, full_name, phone, university_id, role, active,
                           completed_deals, created_at, updated_at)
        values (v_email, crypt(p_password, gen_salt('bf', 10)), v_name, v_phone, p_university_id, 'STUDENT', true,
                0, _utc_now(), _utc_now())
        returning id into v_user_id;
    exception when unique_violation then
        perform _fail('EMAIL_EXISTS', 'Email này đã được đăng ký. Bạn hãy đăng nhập hoặc dùng email khác.');
    end;

    return jsonb_build_object('token', _new_session(v_user_id), 'user', _user_json(v_user_id));
end;
$$;

-- Đăng nhập: sai email hay sai mật khẩu đều báo cùng một câu (không lộ email nào đã đăng ký)
create or replace function api_login(p_email text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user users%rowtype;
begin
    select * into v_user from users where lower(email) = lower(trim(coalesce(p_email, '')));
    if v_user.id is null or not _password_matches(p_password, v_user.password) then
        perform _fail('WRONG_CREDENTIALS', 'Email hoặc mật khẩu không đúng.');
    end if;
    if not coalesce(v_user.active, true) then
        perform _fail('ACCOUNT_LOCKED', 'Tài khoản của bạn đang bị khóa. Vui lòng liên hệ ban quản trị.');
    end if;
    return jsonb_build_object('token', _new_session(v_user.id), 'user', _user_json(v_user.id));
end;
$$;

-- Đăng xuất: xoá phiên (không báo lỗi kể cả khi mã phiên không còn)
create or replace function api_logout(p_token text default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    begin
        delete from app_sessions where token = trim(p_token)::uuid;
    exception when others then
        null;   -- mã phiên sai định dạng: coi như đã đăng xuất
    end;
    return jsonb_build_object('ok', true);
end;
$$;

-- Hồ sơ của người đang đăng nhập
create or replace function api_me(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    return _user_json(_session_user(p_token));
end;
$$;

-- Sửa hồ sơ (ảnh đại diện đổi riêng bằng api_update_avatar)
create or replace function api_update_profile(
    p_token text,
    p_full_name text default null,
    p_phone text default null,
    p_university_id bigint default null,
    p_dorm_area text default null,
    p_bio text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_name    text := trim(coalesce(p_full_name, ''));
    v_phone   text := regexp_replace(coalesce(p_phone, ''), '[\s.-]', '', 'g');
    v_dorm    text := nullif(trim(coalesce(p_dorm_area, '')), '');
    v_bio     text := nullif(trim(coalesce(p_bio, '')), '');
begin
    if char_length(v_name) not between 2 and 100 then
        perform _fail('VALIDATION', 'Họ tên phải từ 2 đến 100 ký tự.');
    end if;
    if v_phone !~ '^(0|\+84)[0-9]{9,10}$' then
        perform _fail('VALIDATION', 'Số điện thoại không hợp lệ (ví dụ 0912345678).');
    end if;
    if p_university_id is null or not exists (select 1 from universities where id = p_university_id) then
        perform _fail('VALIDATION', 'Vui lòng chọn trường của bạn.');
    end if;
    if char_length(coalesce(v_dorm, '')) > 255 then
        perform _fail('VALIDATION', 'Khu vực ở tối đa 255 ký tự.');
    end if;
    if char_length(coalesce(v_bio, '')) > 500 then
        perform _fail('VALIDATION', 'Giới thiệu tối đa 500 ký tự.');
    end if;

    update users
    set full_name = v_name, phone = v_phone, university_id = p_university_id,
        dorm_area = v_dorm, bio = v_bio, updated_at = _utc_now()
    where id = v_user_id;
    return _user_json(v_user_id);
end;
$$;

-- Đổi ảnh đại diện (null = gỡ ảnh, app sẽ hiện chữ cái đầu của tên)
create or replace function api_update_avatar(p_token text, p_avatar_url text default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_url     text := nullif(trim(coalesce(p_avatar_url, '')), '');
begin
    if v_url is not null and (v_url !~* '^https?://' or char_length(v_url) > 1000) then
        perform _fail('VALIDATION', 'Đường dẫn ảnh không hợp lệ, vui lòng tải ảnh lên lại.');
    end if;
    update users set avatar_url = v_url, updated_at = _utc_now() where id = v_user_id;
    return _user_json(v_user_id);
end;
$$;

-- Đổi mật khẩu; các thiết bị khác đang đăng nhập sẽ bị đăng xuất
create or replace function api_change_password(
    p_token text,
    p_current_password text default null,
    p_new_password text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_hash    text;
begin
    select password into v_hash from users where id = v_user_id;
    if not _password_matches(p_current_password, v_hash) then
        perform _fail('VALIDATION', 'Mật khẩu hiện tại không đúng.');
    end if;
    if char_length(coalesce(p_new_password, '')) not between 6 and 72 then
        perform _fail('VALIDATION', 'Mật khẩu mới phải từ 6 đến 72 ký tự.');
    end if;
    if p_new_password = p_current_password then
        perform _fail('VALIDATION', 'Mật khẩu mới phải khác mật khẩu hiện tại.');
    end if;

    update users set password = crypt(p_new_password, gen_salt('bf', 10)), updated_at = _utc_now()
    where id = v_user_id;
    delete from app_sessions where user_id = v_user_id and token <> trim(p_token)::uuid;
    return jsonb_build_object('ok', true);
end;
$$;

-- =================================================================================
-- 8. API TIN ĐĂNG
-- =================================================================================

-- Tìm tin đang bán (APPROVED), không phân biệt hoa thường / có dấu, có phân trang
create or replace function api_search_products(
    p_token text default null,
    p_keyword text default null,
    p_category_id bigint default null,
    p_university_id bigint default null,
    p_sort text default 'NEWEST',
    p_limit int default 20,
    p_offset int default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_viewer   bigint := _session_user_optional(p_token);
    v_keyword  text := nullif(lower(unaccent(trim(coalesce(p_keyword, '')))), '');
    v_sort     text := upper(coalesce(p_sort, 'NEWEST'));
    v_limit    int := least(greatest(coalesce(p_limit, 20), 1), 50);
    v_offset   int := greatest(coalesce(p_offset, 0), 0);
    v_items    jsonb;
    v_has_more boolean;
begin
    with matched as (
        select p.id,
               row_number() over (order by
                   case when v_sort = 'PRICE_ASC' then p.price end asc nulls last,
                   case when v_sort = 'PRICE_DESC' then p.price end desc nulls last,
                   case when v_sort = 'POPULAR' then p.view_count end desc nulls last,
                   p.created_at desc, p.id desc) as position
        from products p
        where p.status = 'APPROVED'
          and (p_category_id is null or p.category_id = p_category_id)
          and (p_university_id is null or p.university_id = p_university_id)
          and (v_keyword is null
               or strpos(lower(unaccent(p.title || ' ' || coalesce(p.description, ''))), v_keyword) > 0)
    )
    -- Lấy dư 1 dòng để biết còn trang sau hay không
    select coalesce(jsonb_agg(_product_json(m.id, v_viewer) order by m.position)
                        filter (where m.position <= v_offset + v_limit), '[]'::jsonb),
           coalesce(bool_or(m.position > v_offset + v_limit), false)
    into v_items, v_has_more
    from matched m
    where m.position > v_offset
      and m.position <= v_offset + v_limit + 1;

    return jsonb_build_object('items', v_items, 'has_more', v_has_more);
end;
$$;

-- Chi tiết tin. Tin chưa duyệt / bị ẩn chỉ chủ tin (hoặc người đã đặt mua) xem được.
create or replace function api_get_product(p_token text default null, p_product_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_viewer  bigint := _session_user_optional(p_token);
    v_product products%rowtype;
begin
    select * into v_product from products where id = p_product_id;
    if v_product.id is null
       or (v_product.status not in ('APPROVED', 'IN_TRANSACTION', 'SOLD')
           and v_product.seller_id is distinct from v_viewer
           and not exists (select 1 from order_requests o
                           where o.product_id = v_product.id and o.buyer_id = v_viewer)) then
        perform _fail('NOT_FOUND', 'Tin đăng không tồn tại hoặc đã bị ẩn.');
    end if;

    -- Tăng lượt xem khi người xem không phải chủ tin
    if v_product.seller_id is distinct from v_viewer then
        update products set view_count = coalesce(view_count, 0) + 1 where id = v_product.id;
    end if;
    return _product_json(v_product.id, v_viewer);
end;
$$;

-- Tin của tôi (mọi trạng thái), mới nhất trước
create or replace function api_my_posts(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_result  jsonb;
begin
    select coalesce(jsonb_agg(_product_json(p.id, v_user_id) order by p.created_at desc, p.id desc), '[]'::jsonb)
    into v_result
    from products p
    where p.seller_id = v_user_id;
    return v_result;
end;
$$;

-- Đăng tin mới -> chờ duyệt (hoặc hiện ngay nếu admin tắt kiểm duyệt)
create or replace function api_create_product(
    p_token text,
    p_title text default null,
    p_description text default null,
    p_price numeric default null,
    p_original_price numeric default null,
    p_is_free boolean default false,
    p_is_urgent boolean default false,
    p_condition text default null,
    p_condition_note text default null,
    p_pass_reason text default null,
    p_category_id bigint default null,
    p_university_id bigint default null,
    p_pickup_spot text default null,
    p_image_urls text[] default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_is_free boolean := coalesce(p_is_free, false);
    v_id      bigint;
begin
    perform _validate_product(p_title, p_description, p_price, p_original_price, v_is_free, p_condition,
                              p_condition_note, p_pass_reason, p_category_id, p_university_id,
                              p_pickup_spot, p_image_urls);

    insert into products (title, description, price, original_price, is_free, is_urgent, item_condition,
                          condition_note, pass_reason, pickup_spot, status, view_count, favorite_count,
                          category_id, university_id, seller_id, created_at, updated_at)
    values (trim(p_title),
            nullif(trim(coalesce(p_description, '')), ''),
            case when v_is_free then 0 else p_price end,
            p_original_price,
            v_is_free,
            coalesce(p_is_urgent, false),
            p_condition,
            nullif(trim(coalesce(p_condition_note, '')), ''),
            nullif(trim(coalesce(p_pass_reason, '')), ''),
            trim(p_pickup_spot),
            _initial_product_status(),
            0, 0,
            p_category_id, p_university_id, v_user_id,
            _utc_now(), _utc_now())
    returning id into v_id;

    perform _save_product_images(v_id, p_image_urls);
    return _product_json(v_id, v_user_id);
end;
$$;

-- Sửa tin -> quay lại chờ duyệt; không sửa được khi đang giao dịch / đã bán
create or replace function api_update_product(
    p_token text,
    p_product_id bigint default null,
    p_title text default null,
    p_description text default null,
    p_price numeric default null,
    p_original_price numeric default null,
    p_is_free boolean default false,
    p_is_urgent boolean default false,
    p_condition text default null,
    p_condition_note text default null,
    p_pass_reason text default null,
    p_category_id bigint default null,
    p_university_id bigint default null,
    p_pickup_spot text default null,
    p_image_urls text[] default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_is_free boolean := coalesce(p_is_free, false);
    v_product products%rowtype;
begin
    select * into v_product from products where id = p_product_id for update;
    if v_product.id is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
    end if;
    if v_product.seller_id <> v_user_id then
        perform _fail('FORBIDDEN', 'Bạn chỉ có thể sửa tin của chính mình.');
    end if;
    if v_product.status in ('IN_TRANSACTION', 'SOLD') then
        perform _fail('CONFLICT', 'Tin đang giao dịch hoặc đã bán nên không thể sửa.');
    end if;

    perform _validate_product(p_title, p_description, p_price, p_original_price, v_is_free, p_condition,
                              p_condition_note, p_pass_reason, p_category_id, p_university_id,
                              p_pickup_spot, p_image_urls);

    update products
    set title = trim(p_title),
        description = nullif(trim(coalesce(p_description, '')), ''),
        price = case when v_is_free then 0 else p_price end,
        original_price = p_original_price,
        is_free = v_is_free,
        is_urgent = coalesce(p_is_urgent, false),
        item_condition = p_condition,
        condition_note = nullif(trim(coalesce(p_condition_note, '')), ''),
        pass_reason = nullif(trim(coalesce(p_pass_reason, '')), ''),
        pickup_spot = trim(p_pickup_spot),
        category_id = p_category_id,
        university_id = p_university_id,
        status = _initial_product_status(),     -- sửa xong phải duyệt lại
        reject_reason = null,
        updated_at = _utc_now()
    where id = v_product.id;

    perform _save_product_images(v_product.id, p_image_urls);
    return _product_json(v_product.id, v_user_id);
end;
$$;

-- Chủ tin đổi trạng thái: ẩn / hiện lại / đánh dấu đã bán
create or replace function api_set_product_status(
    p_token text,
    p_product_id bigint default null,
    p_status text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_target  text := upper(trim(coalesce(p_status, '')));
    v_product products%rowtype;
begin
    select * into v_product from products where id = p_product_id for update;
    if v_product.id is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
    end if;
    if v_product.seller_id <> v_user_id then
        perform _fail('FORBIDDEN', 'Bạn chỉ có thể đổi trạng thái tin của chính mình.');
    end if;

    -- Các bước chuyển chủ tin được phép làm
    if not ((v_product.status = 'APPROVED' and v_target in ('HIDDEN', 'SOLD'))
            or (v_product.status = 'HIDDEN' and v_target = 'APPROVED')
            or (v_product.status = 'IN_TRANSACTION' and v_target = 'SOLD')) then
        perform _fail('CONFLICT', 'Không thể đổi trạng thái tin lúc này. Hãy tải lại trang và thử lại.');
    end if;

    -- Tin bị quản trị viên ẩn thì chủ tin không tự hiện lại được
    if v_target = 'APPROVED' and (
        select l.action from moderation_logs l
        where l.product_id = v_product.id
        order by l.created_at desc, l.id desc
        limit 1) = 'HIDE' then
        perform _fail('FORBIDDEN', 'Tin này đang bị ban quản trị ẩn. Hãy sửa tin để gửi duyệt lại.');
    end if;

    update products set status = v_target, updated_at = _utc_now() where id = v_product.id;

    -- Đã bán: các yêu cầu mua còn chờ tự động bị từ chối
    if v_target = 'SOLD' then
        perform _reject_pending_orders(v_product.id, null, v_user_id, 'Người bán đã đánh dấu món đồ là đã bán.');
    end if;
    return _product_json(v_product.id, v_user_id);
end;
$$;

-- Xoá tin (chỉ khi chưa có đơn nào)
create or replace function api_delete_product(p_token text, p_product_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_seller  bigint;
begin
    select seller_id into v_seller from products where id = p_product_id for update;
    if v_seller is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
    end if;
    if v_seller <> v_user_id then
        perform _fail('FORBIDDEN', 'Bạn chỉ có thể xoá tin của chính mình.');
    end if;
    if exists (select 1 from order_requests where product_id = p_product_id) then
        perform _fail('CONFLICT', 'Tin đã có yêu cầu mua nên không thể xoá. Bạn có thể ẩn tin hoặc đánh dấu đã bán.');
    end if;

    -- Ảnh, lượt lưu, nhật ký kiểm duyệt xoá theo (ON DELETE CASCADE); tin nhắn / báo cáo giữ lại
    delete from products where id = p_product_id;
    return jsonb_build_object('ok', true);
end;
$$;

-- Lưu / bỏ lưu tin yêu thích
create or replace function api_toggle_wishlist(p_token text, p_product_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id   bigint := _session_user(p_token);
    v_product   products%rowtype;
    v_favorited boolean;
begin
    if exists (select 1 from wishlists where user_id = v_user_id and product_id = p_product_id) then
        delete from wishlists where user_id = v_user_id and product_id = p_product_id;
        v_favorited := false;
    else
        select * into v_product from products where id = p_product_id;
        if v_product.id is null or v_product.status not in ('APPROVED', 'IN_TRANSACTION', 'SOLD') then
            perform _fail('NOT_FOUND', 'Tin đăng không tồn tại hoặc đã bị ẩn.');
        end if;
        if v_product.seller_id = v_user_id then
            perform _fail('VALIDATION', 'Bạn không thể lưu tin của chính mình.');
        end if;
        insert into wishlists (user_id, product_id, created_at)
        values (v_user_id, p_product_id, _utc_now())
        on conflict (user_id, product_id) do nothing;
        v_favorited := true;
    end if;

    -- Đếm lại số lượt lưu cho chính xác
    update products
    set favorite_count = (select count(*) from wishlists where product_id = p_product_id)
    where id = p_product_id;
    return jsonb_build_object('favorited', v_favorited);
end;
$$;

-- Danh sách tin đã lưu (chỉ tin còn hiển thị), lưu gần nhất trước
create or replace function api_my_wishlist(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_result  jsonb;
begin
    select coalesce(jsonb_agg(_product_json(p.id, v_user_id) order by w.created_at desc, w.id desc), '[]'::jsonb)
    into v_result
    from wishlists w
    join products p on p.id = w.product_id
    where w.user_id = v_user_id
      and p.status in ('APPROVED', 'IN_TRANSACTION', 'SOLD');
    return v_result;
end;
$$;

-- Báo cáo tin vi phạm -> vào hàng chờ xử lý của trang admin
create or replace function api_report_product(
    p_token text,
    p_product_id bigint default null,
    p_reason text default null,
    p_detail text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_reason  text := upper(trim(coalesce(p_reason, '')));
    v_detail  text := nullif(trim(coalesce(p_detail, '')), '');
    v_seller  bigint;
begin
    if v_reason not in ('FAKE_PRODUCT', 'SPAM', 'INAPPROPRIATE', 'FRAUD', 'OTHER') then
        perform _fail('VALIDATION', 'Vui lòng chọn lý do báo cáo.');
    end if;
    if v_reason = 'OTHER' and v_detail is null then
        perform _fail('VALIDATION', 'Vui lòng mô tả lý do báo cáo.');
    end if;
    if char_length(coalesce(v_detail, '')) > 1000 then
        perform _fail('VALIDATION', 'Mô tả tối đa 1000 ký tự.');
    end if;

    select seller_id into v_seller from products where id = p_product_id;
    if v_seller is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
    end if;
    if v_seller = v_user_id then
        perform _fail('VALIDATION', 'Bạn không thể báo cáo tin của chính mình.');
    end if;
    if exists (select 1 from reports
               where reporter_id = v_user_id and product_id = p_product_id and status = 'PENDING') then
        perform _fail('CONFLICT', 'Bạn đã báo cáo tin này. Ban quản trị đang xem xét.');
    end if;

    insert into reports (reporter_id, product_id, reason, detail, status, created_at)
    values (v_user_id, p_product_id, v_reason, v_detail, 'PENDING', _utc_now());
    return jsonb_build_object('ok', true);
end;
$$;

-- =================================================================================
-- 9. API ĐƠN ĐẶT MUA / HẸN GẶP
-- =================================================================================

-- Xem trước phí sàn
create or replace function api_fee_preview(p_amount numeric default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if p_amount is null or p_amount < 0 then
        perform _fail('VALIDATION', 'Số tiền không hợp lệ.');
    end if;
    return _calc_fee(p_amount) - 'applied_tier_id';
end;
$$;

-- Người mua gửi yêu cầu mua kèm lịch hẹn (điểm hẹn và giờ hẹn do người mua tự chọn)
create or replace function api_place_order(
    p_token text,
    p_product_id bigint default null,
    p_offered_price numeric default null,
    p_meetup_location text default null,
    p_meetup_time text default null,
    p_buyer_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_buyer_id   bigint := _session_user(p_token);
    v_note       text := nullif(trim(coalesce(p_buyer_note, '')), '');
    v_location   text := trim(coalesce(p_meetup_location, ''));
    v_product    products%rowtype;
    v_price      numeric;
    v_fee        jsonb;
    v_order_id   bigint;
    v_buyer_name text;
begin
    -- Khoá dòng tin để hai yêu cầu cùng lúc không tạo đơn trùng
    select * into v_product from products where id = p_product_id for update;
    if v_product.id is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
    end if;
    if v_product.seller_id = v_buyer_id then
        perform _fail('VALIDATION', 'Bạn không thể đặt mua tin của chính mình.');
    end if;
    if v_product.status <> 'APPROVED' then
        perform _fail('CONFLICT', 'Món đồ này hiện không nhận đặt mua (đã bán, đang giao dịch hoặc đã ẩn).');
    end if;
    if exists (select 1 from order_requests
               where product_id = v_product.id and buyer_id = v_buyer_id
                 and status in ('PENDING', 'ACCEPTED', 'DISPUTED')) then
        perform _fail('CONFLICT', 'Bạn đã gửi yêu cầu mua món này. Hãy chờ người bán phản hồi nhé.');
    end if;

    v_price := case when coalesce(v_product.is_free, false) then 0 else round(coalesce(p_offered_price, -1)) end;
    if v_price < 0 or (not coalesce(v_product.is_free, false) and v_price = 0) then
        perform _fail('VALIDATION', 'Vui lòng nhập giá đề nghị lớn hơn 0.');
    end if;
    if v_price > 1000000000 then
        perform _fail('VALIDATION', 'Giá đề nghị không hợp lệ.');
    end if;
    if not (v_location = any (_university_spots(v_product.university_id))) then
        perform _fail('VALIDATION', 'Vui lòng chọn điểm hẹn trong danh sách điểm hẹn của trường.');
    end if;
    perform _check_meetup_time(p_meetup_time);
    if char_length(coalesce(v_note, '')) > 500 then
        perform _fail('VALIDATION', 'Lời nhắn tối đa 500 ký tự.');
    end if;

    -- Phí sàn chốt tại thời điểm tạo đơn
    v_fee := _calc_fee(v_price);
    insert into order_requests (product_id, buyer_id, seller_id, offered_price, meetup_location, meetup_time,
                                buyer_note, status, platform_fee_rate, platform_fee_amount, final_seller_amount,
                                final_buyer_amount, fee_payer, fee_tier_id, payment_status, created_at, updated_at)
    values (v_product.id, v_buyer_id, v_product.seller_id, v_price, v_location, trim(p_meetup_time),
            v_note, 'PENDING',
            (v_fee ->> 'fee_rate')::numeric,
            (v_fee ->> 'platform_fee_amount')::numeric,
            (v_fee ->> 'seller_receive_amount')::numeric,
            (v_fee ->> 'buyer_pay_amount')::numeric,
            v_fee ->> 'fee_payer',
            (v_fee ->> 'applied_tier_id')::bigint,
            'PENDING_PAYMENT', _utc_now(), _utc_now())
    returning id into v_order_id;
    update order_requests set order_code = 'PD' || lpad(v_order_id::text, 6, '0') where id = v_order_id;

    -- Thẻ hẹn gặp trong khung chat với người bán
    insert into chat_messages (sender_id, receiver_id, product_id, content, message_type, offered_price,
                               meetup_location, meetup_time, is_read, created_at)
    values (v_buyer_id, v_product.seller_id, v_product.id,
            coalesce(v_note, 'Mình muốn mua món này. Hẹn gặp bạn theo lịch bên dưới nhé.'),
            'MEETUP', v_price, v_location, trim(p_meetup_time), false, _utc_now());

    select full_name into v_buyer_name from users where id = v_buyer_id;
    perform _notify(v_product.seller_id, 'ORDER_RECEIVED', 'Có yêu cầu mua mới',
        v_buyer_name || ' muốn mua "' || v_product.title || '" với giá ' || _price_text(v_price)
        || ', hẹn tại ' || v_location || ' lúc ' || _meetup_label(trim(p_meetup_time)) || '.',
        v_order_id);
    return _order_json(v_order_id);
end;
$$;

-- Đơn mua (BUYER) hoặc đơn bán (SELLER) của tôi, mới nhất trước
create or replace function api_my_orders(p_token text, p_role text default 'BUYER')
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_role    text := upper(coalesce(p_role, 'BUYER'));
    v_result  jsonb;
begin
    if v_role not in ('BUYER', 'SELLER') then
        perform _fail('VALIDATION', 'Vai trò không hợp lệ.');
    end if;
    select coalesce(jsonb_agg(_order_json(o.id) order by o.created_at desc, o.id desc), '[]'::jsonb)
    into v_result
    from order_requests o
    where (v_role = 'BUYER' and o.buyer_id = v_user_id)
       or (v_role = 'SELLER' and o.seller_id = v_user_id);
    return v_result;
end;
$$;

-- Xử lý đơn: ACCEPT / REJECT (người bán), CANCEL (người mua), COMPLETE / DISPUTE (cả hai bên)
create or replace function api_update_order(
    p_token text,
    p_order_id bigint default null,
    p_action text default null,
    p_reason text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id     bigint := _session_user(p_token);
    v_action      text := upper(trim(coalesce(p_action, '')));
    v_reason      text := nullif(trim(coalesce(p_reason, '')), '');
    v_order       order_requests%rowtype;
    v_product     products%rowtype;
    v_is_buyer    boolean;
    v_partner_id  bigint;
    v_meetup      text;
    v_with_reason text := '';
begin
    select * into v_order from order_requests where id = p_order_id for update;
    if v_order.id is null then
        perform _fail('NOT_FOUND', 'Không tìm thấy đơn.');
    end if;
    if v_user_id not in (v_order.buyer_id, v_order.seller_id) then
        perform _fail('FORBIDDEN', 'Bạn không có quyền xử lý đơn này.');
    end if;
    if char_length(coalesce(v_reason, '')) > 500 then
        perform _fail('VALIDATION', 'Lý do tối đa 500 ký tự.');
    end if;

    select * into v_product from products where id = v_order.product_id for update;
    v_is_buyer := v_user_id = v_order.buyer_id;
    v_partner_id := case when v_is_buyer then v_order.seller_id else v_order.buyer_id end;
    v_meetup := coalesce(v_order.meetup_location, 'điểm đã hẹn') || ' lúc ' || _meetup_label(v_order.meetup_time);
    if v_reason is not null then
        v_with_reason := ' Lý do: ' || v_reason;
    end if;

    if v_action = 'ACCEPT' then
        if v_is_buyer then
            perform _fail('FORBIDDEN', 'Chỉ người bán mới chấp nhận được yêu cầu mua.');
        end if;
        if v_order.status <> 'PENDING' then
            perform _fail('CONFLICT', 'Yêu cầu này đã được xử lý trước đó.');
        end if;
        if v_product.status not in ('APPROVED', 'HIDDEN') then
            perform _fail('CONFLICT', 'Tin chưa sẵn sàng để chốt (đang chờ duyệt, đang giao dịch hoặc đã bán).');
        end if;

        update order_requests
        set status = 'ACCEPTED', confirmed_at = _utc_now(), status_updated_by = v_user_id, updated_at = _utc_now()
        where id = v_order.id;
        update products set status = 'IN_TRANSACTION', updated_at = _utc_now() where id = v_product.id;

        perform _system_message(v_user_id, v_order.buyer_id, v_product.id,
            'Người bán đã chấp nhận yêu cầu mua. Hẹn gặp tại ' || v_meetup || '.');
        perform _notify(v_order.buyer_id, 'ORDER_ACCEPTED', 'Người bán đã chấp nhận',
            'Yêu cầu mua "' || v_product.title || '" đã được chấp nhận. Hẹn gặp tại ' || v_meetup || '.',
            v_order.id);
        -- Món đồ đã chốt với người này: các yêu cầu khác tự động bị từ chối
        perform _reject_pending_orders(v_product.id, v_order.id, v_user_id,
            'Người bán đã chốt món đồ với người mua khác.');

    elsif v_action in ('REJECT', 'CANCEL') then
        if v_action = 'REJECT' and v_is_buyer then
            perform _fail('FORBIDDEN', 'Người mua hãy dùng chức năng Hủy đơn.');
        end if;
        if v_action = 'CANCEL' and not v_is_buyer then
            perform _fail('FORBIDDEN', 'Người bán hãy dùng chức năng Từ chối.');
        end if;
        if v_order.status not in ('PENDING', 'ACCEPTED', 'DISPUTED') then
            perform _fail('CONFLICT', 'Đơn này đã kết thúc.');
        end if;

        update order_requests
        set status = case when v_action = 'REJECT' then 'REJECTED' else 'CANCELLED' end,
            cancel_reason = v_reason, status_updated_by = v_user_id, updated_at = _utc_now()
        where id = v_order.id;

        -- Đơn đã chốt bị huỷ: mở bán lại món đồ
        if v_order.status in ('ACCEPTED', 'DISPUTED') and v_product.status = 'IN_TRANSACTION' then
            update products set status = 'APPROVED', updated_at = _utc_now() where id = v_product.id;
        end if;

        if v_action = 'REJECT' then
            perform _system_message(v_user_id, v_partner_id, v_product.id,
                'Người bán đã từ chối yêu cầu mua.' || v_with_reason);
            perform _notify(v_partner_id, 'ORDER_REJECTED', 'Yêu cầu mua bị từ chối',
                'Người bán đã từ chối yêu cầu mua "' || v_product.title || '".' || v_with_reason, v_order.id);
        else
            perform _system_message(v_user_id, v_partner_id, v_product.id,
                'Người mua đã hủy yêu cầu mua.' || v_with_reason);
            perform _notify(v_partner_id, 'ORDER_CANCELLED', 'Đơn đã bị hủy',
                'Người mua đã hủy đơn "' || v_product.title || '".' || v_with_reason, v_order.id);
        end if;

    elsif v_action = 'COMPLETE' then
        if v_order.status <> 'ACCEPTED' then
            perform _fail('CONFLICT', 'Chỉ hoàn tất được đơn đã chốt hẹn.');
        end if;

        update order_requests
        set status = 'COMPLETED', completed_at = _utc_now(), payment_status = 'COMPLETED',
            status_updated_by = v_user_id, updated_at = _utc_now()
        where id = v_order.id;
        update products set status = 'SOLD', updated_at = _utc_now() where id = v_product.id;
        update users set completed_deals = coalesce(completed_deals, 0) + 1
        where id in (v_order.buyer_id, v_order.seller_id);

        -- Ghi nhận giao dịch để tính doanh thu phí sàn (trang admin)
        insert into transactions (order_id, buyer_id, seller_id, product_id, transaction_amount,
                                  platform_fee_rate, platform_fee_amount, seller_receive_amount, created_at)
        select v_order.id, v_order.buyer_id, v_order.seller_id, v_order.product_id, v_order.offered_price,
               coalesce(v_order.platform_fee_rate, 0), coalesce(v_order.platform_fee_amount, 0),
               coalesce(v_order.final_seller_amount, v_order.offered_price), _utc_now()
        where not exists (select 1 from transactions where order_id = v_order.id);

        perform _system_message(v_user_id, v_partner_id, v_product.id,
            'Giao dịch đã hoàn tất. Cảm ơn hai bạn đã mua bán an toàn trên Chợ Đồ Cũ.');
        perform _notify(v_partner_id, 'ORDER_COMPLETED', 'Giao dịch hoàn tất',
            'Đơn "' || v_product.title || '" đã được xác nhận hoàn tất.', v_order.id);

    elsif v_action = 'DISPUTE' then
        if v_order.status <> 'ACCEPTED' then
            perform _fail('CONFLICT', 'Chỉ khiếu nại được đơn đã chốt hẹn.');
        end if;
        if v_reason is null then
            perform _fail('VALIDATION', 'Vui lòng nhập lý do khiếu nại.');
        end if;

        update order_requests
        set status = 'DISPUTED', dispute_reason = v_reason, status_updated_by = v_user_id, updated_at = _utc_now()
        where id = v_order.id;

        perform _system_message(v_user_id, v_partner_id, v_product.id,
            (case when v_is_buyer then 'Người mua' else 'Người bán' end)
            || ' đã gửi khiếu nại.' || v_with_reason || ' Ban quản trị sẽ xem xét và liên hệ hai bạn.');
        perform _notify(v_partner_id, 'ORDER_DISPUTED', 'Đơn đang bị khiếu nại',
            'Đơn "' || v_product.title || '" đang được khiếu nại. Ban quản trị sẽ xem xét.', v_order.id);

    else
        perform _fail('VALIDATION', 'Thao tác không hợp lệ.');
    end if;

    return _order_json(v_order.id);
end;
$$;

-- =================================================================================
-- 10. API CHAT
-- =================================================================================

-- Hộp thư: mỗi người chat cùng là một dòng, cuộc trò chuyện mới nhất lên đầu
create or replace function api_conversations(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_result  jsonb;
begin
    with my_messages as (
        select m.*,
               case when m.sender_id = v_user_id then m.receiver_id else m.sender_id end as partner_id
        from chat_messages m
        where m.sender_id = v_user_id or m.receiver_id = v_user_id
    ),
    last_message as (       -- tin nhắn mới nhất với từng người
        select distinct on (partner_id) *
        from my_messages
        order by partner_id, created_at desc, id desc
    ),
    last_product as (       -- món đồ của tin nhắn gần nhất có gắn món đồ
        select distinct on (partner_id) partner_id, product_id
        from my_messages
        where product_id is not null
        order by partner_id, created_at desc, id desc
    ),
    unread as (
        select sender_id as partner_id, count(*) as total
        from chat_messages
        where receiver_id = v_user_id and not coalesce(is_read, false)
        group by sender_id
    )
    select coalesce(jsonb_agg(jsonb_build_object(
               'partner_id', partner.id,
               'partner_name', partner.full_name,
               'partner_avatar_url', partner.avatar_url,
               'product_id', p.id,
               'product_title', p.title,
               'product_image', _cover_image(p.id),
               'product_price', p.price,
               'last_message', lm.content,
               'last_message_type', coalesce(lm.message_type, 'TEXT'),
               'last_message_mine', lm.sender_id = v_user_id,
               'last_message_at', _iso(lm.created_at),
               'unread_count', coalesce(u.total, 0)
           ) order by lm.created_at desc, lm.id desc), '[]'::jsonb)
    into v_result
    from last_message lm
    join users partner on partner.id = lm.partner_id
    left join last_product lp on lp.partner_id = lm.partner_id
    left join products p on p.id = lp.product_id
    left join unread u on u.partner_id = lm.partner_id;
    return v_result;
end;
$$;

-- 200 tin nhắn gần nhất với một người, xếp từ cũ đến mới
create or replace function api_messages(p_token text, p_partner_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_result  jsonb;
begin
    select coalesce(jsonb_agg(_message_json(t.id) order by t.created_at, t.id), '[]'::jsonb)
    into v_result
    from (
        select m.id, m.created_at
        from chat_messages m
        where (m.sender_id = v_user_id and m.receiver_id = p_partner_id)
           or (m.sender_id = p_partner_id and m.receiver_id = v_user_id)
        order by m.created_at desc, m.id desc
        limit 200
    ) t;
    return v_result;
end;
$$;

-- Gửi tin nhắn: TEXT (chữ), IMAGE (ảnh), OFFER (trả giá), MEETUP (đề xuất hẹn gặp)
create or replace function api_send_message(
    p_token text,
    p_receiver_id bigint default null,
    p_product_id bigint default null,
    p_message_type text default 'TEXT',
    p_content text default null,
    p_image_url text default null,
    p_offered_price numeric default null,
    p_meetup_location text default null,
    p_meetup_time text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_sender_id bigint := _session_user(p_token);
    v_type      text := upper(trim(coalesce(p_message_type, 'TEXT')));
    v_content   text := trim(coalesce(p_content, ''));
    v_image     text;
    v_price     numeric;
    v_location  text;
    v_time      text;
    v_product   products%rowtype;
    v_id        bigint;
begin
    if p_receiver_id is null or not exists (select 1 from users where id = p_receiver_id) then
        perform _fail('NOT_FOUND', 'Người nhận không tồn tại.');
    end if;
    if p_receiver_id = v_sender_id then
        perform _fail('VALIDATION', 'Bạn không thể nhắn tin cho chính mình.');
    end if;
    if v_type not in ('TEXT', 'IMAGE', 'OFFER', 'MEETUP') then
        perform _fail('VALIDATION', 'Loại tin nhắn không hợp lệ.');
    end if;
    if char_length(v_content) > 1000 then
        perform _fail('VALIDATION', 'Tin nhắn tối đa 1000 ký tự.');
    end if;

    -- Món đồ gắn kèm (nếu có) phải là đồ của một trong hai người
    if p_product_id is not null then
        select * into v_product from products where id = p_product_id;
        if v_product.id is null then
            perform _fail('NOT_FOUND', 'Không tìm thấy tin đăng.');
        end if;
        if v_product.seller_id not in (v_sender_id, p_receiver_id) then
            perform _fail('VALIDATION', 'Món đồ này không thuộc cuộc trò chuyện.');
        end if;
    elsif v_type in ('OFFER', 'MEETUP') then
        perform _fail('VALIDATION', 'Vui lòng chọn món đồ cần trả giá hoặc hẹn gặp.');
    end if;

    if v_type = 'TEXT' then
        if v_content = '' then
            perform _fail('VALIDATION', 'Vui lòng nhập nội dung tin nhắn.');
        end if;

    elsif v_type = 'IMAGE' then
        v_image := trim(coalesce(p_image_url, ''));
        if v_image !~* '^https?://' or char_length(v_image) > 1000 then
            perform _fail('VALIDATION', 'Ảnh gửi kèm không hợp lệ, vui lòng chọn lại ảnh.');
        end if;

    elsif v_type = 'OFFER' then
        v_price := round(coalesce(p_offered_price, -1));
        if v_price < 0 or v_price > 1000000000 then
            perform _fail('VALIDATION', 'Vui lòng nhập giá đề nghị hợp lệ.');
        end if;
        if v_content = '' then
            v_content := 'Mình đề nghị giá ' || _price_text(v_price) || '.';
        end if;

    else -- MEETUP
        v_location := trim(coalesce(p_meetup_location, ''));
        if not (v_location = any (_university_spots(v_product.university_id))) then
            perform _fail('VALIDATION', 'Vui lòng chọn điểm hẹn trong danh sách điểm hẹn của trường.');
        end if;
        perform _check_meetup_time(p_meetup_time);
        v_time := trim(p_meetup_time);
        if p_offered_price is not null and p_offered_price >= 0 then
            v_price := round(p_offered_price);
        end if;
        if v_content = '' then
            v_content := 'Mình đề xuất hẹn gặp tại ' || v_location || ' lúc ' || _meetup_label(v_time) || '.';
        end if;
    end if;

    insert into chat_messages (sender_id, receiver_id, product_id, content, message_type, image_url,
                               offered_price, meetup_location, meetup_time, is_read, created_at)
    values (v_sender_id, p_receiver_id, v_product.id, v_content, v_type, v_image,
            v_price, v_location, v_time, false, _utc_now())
    returning id into v_id;
    return _message_json(v_id);
end;
$$;

-- Đánh dấu đã đọc mọi tin nhắn người kia gửi cho mình
create or replace function api_mark_read(p_token text, p_partner_id bigint default null)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_updated int;
begin
    update chat_messages
    set is_read = true
    where receiver_id = v_user_id
      and sender_id = p_partner_id
      and not coalesce(is_read, false);
    get diagnostics v_updated = row_count;
    return jsonb_build_object('updated', v_updated);
end;
$$;

-- Số tin nhắn chưa đọc (huy hiệu tab Tin nhắn)
create or replace function api_unread_count(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
begin
    return jsonb_build_object('count', (
        select count(*) from chat_messages
        where receiver_id = v_user_id and not coalesce(is_read, false)));
end;
$$;

-- =================================================================================
-- 11. API THÔNG BÁO
-- =================================================================================

-- 100 thông báo gần nhất
create or replace function api_my_notifications(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_result  jsonb;
begin
    select coalesce(jsonb_agg(jsonb_build_object(
               'id', n.id,
               'type', n.type,
               'title', n.title,
               'message', n.message,
               'reference_id', n.reference_id,
               'is_read', coalesce(n.is_read, false),
               'created_at', _iso(n.created_at)
           ) order by n.created_at desc, n.id desc), '[]'::jsonb)
    into v_result
    from (
        select * from notifications
        where user_id = v_user_id
        order by created_at desc, id desc
        limit 100
    ) n;
    return v_result;
end;
$$;

-- Số thông báo chưa đọc (chấm đỏ trên chuông)
create or replace function api_notification_unread_count(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
begin
    return jsonb_build_object('count', (
        select count(*) from notifications
        where user_id = v_user_id and not coalesce(is_read, false)));
end;
$$;

-- Đánh dấu đã đọc mọi thông báo
create or replace function api_mark_notifications_read(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_user_id bigint := _session_user(p_token);
    v_updated int;
begin
    update notifications set is_read = true
    where user_id = v_user_id and not coalesce(is_read, false);
    get diagnostics v_updated = row_count;
    return jsonb_build_object('updated', v_updated);
end;
$$;

-- =================================================================================
-- 12. QUYỀN TRUY CẬP
-- =================================================================================

-- App (anon / authenticated) không được đụng trực tiếp vào bảng gốc
revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
grant usage on schema public to anon, authenticated;

-- Bật RLS cho mọi bảng (hàm SECURITY DEFINER chạy bằng quyền chủ bảng nên vẫn hoạt động)
do $$
declare
    t record;
begin
    for t in select tablename from pg_tables where schemaname = 'public' loop
        execute format('alter table public.%I enable row level security', t.tablename);
    end loop;
end $$;

-- Chỉ danh mục và danh sách trường là đọc công khai (màn đăng ký, bộ lọc)
grant select on categories, universities to anon, authenticated;
drop policy if exists "categories public read" on categories;
create policy "categories public read" on categories for select to anon, authenticated using (true);
drop policy if exists "universities public read" on universities;
create policy "universities public read" on universities for select to anon, authenticated using (true);

-- Hàm _xxx chỉ dùng nội bộ; hàm api_* cho app gọi qua /rest/v1/rpc
do $$
declare
    f record;
begin
    for f in
        select p.oid::regprocedure as signature, p.proname
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and (p.proname like 'api\_%' or p.proname like '\_%')
          and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e')
    loop
        execute format('revoke all on function %s from public, anon, authenticated', f.signature);
        if f.proname like 'api\_%' then
            execute format('grant execute on function %s to anon, authenticated', f.signature);
        end if;
    end loop;
end $$;

-- =================================================================================
-- 13. KHO ẢNH (Supabase Storage): bucket "passuni", thư mục products / avatars / chat
--     Tự bỏ qua khi chạy trên PostgreSQL thường (không có schema storage).
-- =================================================================================
do $$
begin
    if to_regclass('storage.buckets') is null then
        raise notice 'Không có Supabase Storage — bỏ qua phần kho ảnh.';
        return;
    end if;

    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values ('passuni', 'passuni', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
    on conflict (id) do update
        set public = true,
            file_size_limit = excluded.file_size_limit,
            allowed_mime_types = excluded.allowed_mime_types;

    execute 'drop policy if exists "passuni public read" on storage.objects';
    execute 'create policy "passuni public read" on storage.objects
                 for select to anon, authenticated using (bucket_id = ''passuni'')';

    -- Chỉ cho tải ảnh MỚI vào đúng 3 thư mục; không cho sửa / xoá ảnh đã có
    execute 'drop policy if exists "passuni anon upload" on storage.objects';
    execute 'create policy "passuni anon upload" on storage.objects
                 for insert to anon, authenticated
                 with check (bucket_id = ''passuni''
                             and (storage.foldername(name))[1] in (''products'', ''avatars'', ''chat''))';
exception when insufficient_privilege then
    raise warning 'Không đủ quyền cấu hình Storage (%). Hãy tạo bucket public "passuni" trong Dashboard > Storage.', sqlerrm;
end $$;

-- Báo PostgREST nạp lại danh sách hàm mới
notify pgrst, 'reload schema';

-- =================================================================================
-- KIỂM TRA: phải thấy 29 hàm API và bucket ảnh đã sẵn sàng
-- =================================================================================
select
    (select count(*)
     from pg_proc p
     join pg_namespace n on n.oid = p.pronamespace
     where n.nspname = 'public' and p.proname like 'api\_%') as so_ham_api,
    (select count(*) from app_sessions) as so_phien_dang_nhap;
