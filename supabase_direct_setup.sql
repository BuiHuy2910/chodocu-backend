-- =================================================================================
-- PASSUNI — CHẾ ĐỘ APP GỌI THẲNG SUPABASE (KHÔNG CẦN BACKEND)
-- Chạy file này SAU database_schema.sql và seed_data.sql, trong Supabase SQL Editor.
--
-- Nguyên tắc bảo mật:
--   * App chỉ dùng anon key -> KHÔNG có quyền đọc/ghi trực tiếp bảng gốc.
--   * Đọc công khai: view v_public_products (chỉ bài APPROVED) + categories/universities.
--   * Mọi thao tác ghi đi qua RPC SECURITY DEFINER, xác thực bằng session token
--     lưu trong bảng app_sessions (sinh khi đăng nhập).
-- =================================================================================

create extension if not exists pgcrypto;

-- =================================================================================
-- 1. PHIÊN ĐĂNG NHẬP (token do server cấp, không dùng JWT của backend)
-- =================================================================================
create table if not exists app_sessions (
    token uuid primary key default gen_random_uuid(),
    user_id bigint not null references users(id) on delete cascade,
    created_at timestamptz not null default now()
);

create index if not exists idx_app_sessions_user on app_sessions(user_id);

create or replace function _session_user(p_token text)
returns bigint
language plpgsql
security definer
set search_path = public, extensions
as $$
declare v_id bigint;
begin
    if p_token is null or length(trim(p_token)) = 0 then
        raise exception 'INVALID_SESSION';
    end if;
    select user_id into v_id
    from app_sessions
    where token::text = trim(p_token)
    limit 1;
    if v_id is null then
        raise exception 'INVALID_SESSION';
    end if;
    return v_id;
end;
$$;

-- =================================================================================
-- 2. VIEW ĐỌC (dữ liệu đã làm phẳng để app map trực tiếp)
-- =================================================================================

create or replace view v_products as
select
    p.id, p.title, p.description, p.price, p.original_price, p.is_free, p.is_urgent,
    p.item_condition, p.condition_note, p.pass_reason, p.pickup_spot,
    p.status, p.reject_reason, p.view_count, p.favorite_count,
    p.created_at, p.updated_at,
    p.category_id, c.name as category_name, c.icon as category_icon,
    p.university_id, u.name as university_name, u.short_name as university_short_name,
    p.seller_id, s.full_name as seller_name, s.avatar_url as seller_avatar_url,
    s.verified_student as seller_verified, s.rating as seller_rating,
    s.completed_deals as seller_completed_deals,
    su.short_name as seller_university_short_name,
    coalesce((
        select jsonb_agg(pi.image_url order by pi.image_url)
        from product_images pi where pi.product_id = p.id
    ), '[]'::jsonb) as image_urls
from products p
left join categories c on c.id = p.category_id
left join universities u on u.id = p.university_id
left join users s on s.id = p.seller_id
left join universities su on su.id = s.university_id;

-- Bản công khai: chỉ bài đã duyệt
create or replace view v_public_products as
select * from v_products where status = 'APPROVED';

create or replace view v_user_profiles as
select
    us.id, us.email, us.full_name, us.student_id, us.phone, us.avatar_url, us.bio,
    us.dorm_area, us.role, us.verified_student, us.active, us.rating, us.completed_deals,
    us.university_id, uni.name as university_name, uni.short_name as university_short_name
from users us
left join universities uni on uni.id = us.university_id;

create or replace view v_orders as
select
    o.id, o.order_code, o.offered_price, o.meetup_location, o.meetup_time,
    o.buyer_note, o.seller_note, o.status, o.payment_status, o.fee_payer,
    o.platform_fee_rate, o.platform_fee_amount, o.final_seller_amount, o.final_buyer_amount,
    o.created_at, o.completed_at,
    b.id as buyer_id, b.full_name as buyer_name, b.avatar_url as buyer_avatar_url,
    s.id as seller_id, s.full_name as seller_name, s.avatar_url as seller_avatar_url,
    p.id as product_id, p.title as product_title, p.price as product_price,
    p.description as product_description, p.item_condition as product_condition,
    p.university_id as product_university_id, p.category_id as product_category_id,
    (select pi.image_url from product_images pi where pi.product_id = p.id limit 1) as product_image
from order_requests o
join products p on p.id = o.product_id
join users b on b.id = o.buyer_id
join users s on s.id = o.seller_id;

create or replace view v_chat_messages as
select
    m.id, m.content, m.message_type, m.offered_price, m.meetup_location, m.meetup_time,
    m.created_at,
    m.sender_id, s.full_name as sender_name, s.avatar_url as sender_avatar_url,
    m.receiver_id, r.full_name as receiver_name, r.avatar_url as receiver_avatar_url,
    m.product_id, p.title as product_title
from chat_messages m
join users s on s.id = m.sender_id
join users r on r.id = m.receiver_id
left join products p on p.id = m.product_id;

-- =================================================================================
-- 3. TÍNH PHÍ SÀN (cùng công thức với backend)
-- =================================================================================
create or replace function _calc_fee(p_amount numeric)
returns table(out_rate numeric, out_fee numeric, out_seller numeric, out_buyer numeric, out_payer text)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_enabled boolean;
    v_rate numeric;
    v_min numeric;
    v_max numeric;
    v_step numeric;
    v_payer text;
    v_fee numeric;
begin
    select (value = 'true') into v_enabled from platform_settings where setting_key = 'fee_enabled';
    select value::numeric into v_min from platform_settings where setting_key = 'min_platform_fee';
    select value::numeric into v_max from platform_settings where setting_key = 'max_platform_fee';
    select value::numeric into v_step from platform_settings where setting_key = 'fee_round_step';
    select upper(value) into v_payer from platform_settings where setting_key = 'fee_payer';

    v_enabled := coalesce(v_enabled, true);
    v_payer := coalesce(v_payer, 'SELLER');

    select t.fee_rate into v_rate
    from platform_fee_tiers t
    where t.active
      and p_amount >= t.min_amount
      and (t.max_amount is null or p_amount <= t.max_amount)
    order by t.priority desc
    limit 1;

    if v_rate is null then
        select value::numeric into v_rate from platform_settings where setting_key = 'platform_fee_rate';
    end if;
    v_rate := coalesce(v_rate, 0);

    v_fee := 0;
    if v_enabled then
        v_fee := round(p_amount * v_rate);
        if coalesce(v_step, 0) > 0 then
            v_fee := round(v_fee / v_step) * v_step;
        end if;
        if v_fee < coalesce(v_min, 0) then v_fee := v_min; end if;
        if coalesce(v_max, 0) > 0 and v_fee > v_max then v_fee := v_max; end if;
        if v_fee > p_amount then v_fee := p_amount; end if;
    end if;

    out_rate := v_rate;
    out_fee := v_fee;
    out_payer := v_payer;

    if v_payer = 'BUYER' then
        out_seller := p_amount;
        out_buyer := p_amount + v_fee;
    elsif v_payer = 'SPLIT' then
        out_seller := p_amount - ceil(v_fee / 2.0);
        out_buyer := p_amount + floor(v_fee / 2.0);
    else
        out_seller := p_amount - v_fee;
        out_buyer := p_amount;
    end if;
    return next;
end;
$$;

create or replace function api_fee_preview(p_amount numeric)
returns table(out_rate numeric, out_fee numeric, out_seller numeric, out_buyer numeric, out_payer text)
language sql
security definer
set search_path = public, extensions
as $$
    select * from _calc_fee(coalesce(p_amount, 0));
$$;

-- =================================================================================
-- 4. AUTH
-- =================================================================================
create or replace function api_login(p_email text, p_password text)
returns table(out_token text, out_user_id bigint)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    u users;
    v_token text;
begin
    select * into u from users where lower(email) = lower(trim(p_email)) limit 1;
    if u.id is null then
        raise exception 'EMAIL_NOT_FOUND';
    end if;
    if u.password <> crypt(p_password, u.password) then
        raise exception 'WRONG_PASSWORD';
    end if;
    if u.active is false then
        raise exception 'ACCOUNT_LOCKED';
    end if;

    insert into app_sessions(user_id) values (u.id)
    returning token::text into v_token;

    out_token := v_token;
    out_user_id := u.id;
    return next;
end;
$$;

create or replace function api_register(
    p_email text,
    p_password text,
    p_full_name text,
    p_student_id text default null,
    p_phone text default null,
    p_university_id bigint default null,
    p_dorm_area text default null
)
returns table(out_token text, out_user_id bigint)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_email text := lower(trim(p_email));
    v_uni bigint;
    v_user_id bigint;
    v_token text;
begin
    if v_email is null or v_email = '' then raise exception 'EMAIL_REQUIRED'; end if;
    if p_password is null or length(p_password) < 6 then raise exception 'PASSWORD_TOO_SHORT'; end if;
    if exists (select 1 from users where lower(email) = v_email) then
        raise exception 'EMAIL_EXISTS';
    end if;

    v_uni := coalesce(p_university_id, (select id from universities order by id limit 1));

    insert into users (email, password, full_name, student_id, phone, university_id, dorm_area,
                       avatar_url, role, verified_student, active, rating, completed_deals)
    values (v_email, crypt(p_password, gen_salt('bf')), trim(p_full_name),
            nullif(trim(coalesce(p_student_id, '')), ''),
            nullif(trim(coalesce(p_phone, '')), ''),
            v_uni,
            coalesce(nullif(trim(coalesce(p_dorm_area, '')), ''), 'KTX Khu B'),
            'https://api.dicebear.com/7.x/notionists/svg?seed=' || v_email,
            'STUDENT', true, true, 5.0, 0)
    returning id into v_user_id;

    insert into app_sessions(user_id) values (v_user_id) returning token::text into v_token;

    out_token := v_token;
    out_user_id := v_user_id;
    return next;
end;
$$;

create or replace function api_logout(p_token text)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    delete from app_sessions where token::text = trim(p_token);
    return true;
end;
$$;

create or replace function api_me(p_token text)
returns setof v_user_profiles
language sql
security definer
set search_path = public, extensions
as $$
    select * from v_user_profiles where id = _session_user(p_token);
$$;

create or replace function api_update_profile(
    p_token text,
    p_full_name text default null,
    p_phone text default null,
    p_dorm_area text default null,
    p_bio text default null,
    p_avatar_url text default null
)
returns setof v_user_profiles
language plpgsql
security definer
set search_path = public, extensions
as $$
declare v_id bigint := _session_user(p_token);
begin
    update users set
        full_name = coalesce(nullif(trim(p_full_name), ''), full_name),
        phone = coalesce(p_phone, phone),
        dorm_area = coalesce(p_dorm_area, dorm_area),
        bio = coalesce(p_bio, bio),
        avatar_url = coalesce(p_avatar_url, avatar_url),
        updated_at = now()
    where id = v_id;

    return query select * from v_user_profiles where id = v_id;
end;
$$;

-- =================================================================================
-- 5. SẢN PHẨM
-- =================================================================================
create or replace function api_my_posts(p_token text)
returns setof v_products
language sql
security definer
set search_path = public, extensions
as $$
    select * from v_products where seller_id = _session_user(p_token) order by created_at desc;
$$;

create or replace function api_create_product(
    p_token text,
    p_title text,
    p_description text,
    p_price numeric,
    p_is_free boolean default false,
    p_is_urgent boolean default false,
    p_condition text default 'LIKE_NEW_95',
    p_condition_note text default null,
    p_pickup_spot text default null,
    p_category_id bigint default null,
    p_university_id bigint default null,
    p_images text[] default null
)
returns setof v_products
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_uid bigint := _session_user(p_token);
    v_role text;
    v_require_approval boolean;
    v_status text;
    v_product_id bigint;
    v_img text;
begin
    if p_title is null or length(trim(p_title)) = 0 then raise exception 'TITLE_REQUIRED'; end if;

    select role into v_role from users where id = v_uid;
    select (value = 'true') into v_require_approval
      from platform_settings where setting_key = 'post_require_approval';
    v_require_approval := coalesce(v_require_approval, true);

    v_status := case
        when v_role in ('ADMIN', 'MODERATOR') or v_require_approval = false then 'APPROVED'
        else 'PENDING'
    end;

    insert into products (title, description, price, is_free, is_urgent, item_condition,
                          condition_note, pickup_spot, status, category_id, university_id, seller_id)
    values (trim(p_title), p_description,
            case when p_is_free then 0 else coalesce(p_price, 0) end,
            coalesce(p_is_free, false), coalesce(p_is_urgent, false),
            coalesce(p_condition, 'LIKE_NEW_95'), p_condition_note, p_pickup_spot,
            v_status,
            coalesce(p_category_id, (select id from categories order by id limit 1)),
            coalesce(p_university_id, (select university_id from users where id = v_uid)),
            v_uid)
    returning id into v_product_id;

    if p_images is not null then
        foreach v_img in array p_images loop
            if v_img is not null and length(trim(v_img)) > 0 then
                insert into product_images(product_id, image_url) values (v_product_id, trim(v_img));
            end if;
        end loop;
    end if;

    return query select * from v_products where id = v_product_id;
end;
$$;

create or replace function api_delete_product(p_token text, p_product_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare v_uid bigint := _session_user(p_token);
begin
    if not exists (select 1 from products where id = p_product_id and seller_id = v_uid) then
        raise exception 'NOT_OWNER';
    end if;
    delete from products where id = p_product_id;
    return true;
end;
$$;

-- =================================================================================
-- 6. ĐƠN HÀNG
-- =================================================================================
create or replace function api_my_orders(p_token text)
returns setof v_orders
language sql
security definer
set search_path = public, extensions
as $$
    select * from v_orders
    where buyer_id = _session_user(p_token) or seller_id = _session_user(p_token)
    order by created_at desc;
$$;

create or replace function api_my_wishlist(p_token text)
returns setof v_products
language sql
security definer
set search_path = public, extensions
as $$
    select p.* from v_products p
    join wishlists w on w.product_id = p.id
    where w.user_id = _session_user(p_token)
    order by w.created_at desc;
$$;

create or replace function api_toggle_wishlist(p_token text, p_product_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare v_uid bigint := _session_user(p_token);
begin
    if exists (select 1 from wishlists where user_id = v_uid and product_id = p_product_id) then
        delete from wishlists where user_id = v_uid and product_id = p_product_id;
        return false;
    else
        insert into wishlists(user_id, product_id) values (v_uid, p_product_id);
        return true;
    end if;
end;
$$;

create or replace function api_place_order(
    p_token text,
    p_product_id bigint,
    p_offered_price numeric default null,
    p_meetup_location text default null,
    p_meetup_time text default null,
    p_buyer_note text default null
)
returns setof v_orders
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_buyer bigint := _session_user(p_token);
    v_product products;
    v_price numeric;
    v_fee record;
    v_order_id bigint;
begin
    select * into v_product from products where id = p_product_id;
    if v_product.id is null then raise exception 'PRODUCT_NOT_FOUND'; end if;
    if v_product.seller_id = v_buyer then raise exception 'CANNOT_BUY_OWN'; end if;
    if v_product.status = 'SOLD' then raise exception 'PRODUCT_SOLD'; end if;
    if v_product.status <> 'APPROVED' then raise exception 'PRODUCT_NOT_AVAILABLE'; end if;

    v_price := coalesce(p_offered_price, v_product.price);
    select * into v_fee from _calc_fee(v_price);

    insert into order_requests (product_id, buyer_id, seller_id, offered_price,
        meetup_location, meetup_time, buyer_note, status,
        platform_fee_rate, platform_fee_amount, final_seller_amount, final_buyer_amount, fee_payer)
    values (p_product_id, v_buyer, v_product.seller_id, v_price,
        coalesce(p_meetup_location, v_product.pickup_spot), coalesce(p_meetup_time, 'Hôm nay'),
        p_buyer_note, 'PENDING',
        v_fee.out_rate, v_fee.out_fee, v_fee.out_seller, v_fee.out_buyer, v_fee.out_payer)
    returning id into v_order_id;

    update order_requests set order_code = 'PD' || lpad(id::text, 6, '0') where id = v_order_id;

    insert into notifications(user_id, type, title, message, reference_id)
    values (v_product.seller_id, 'ORDER_RECEIVED', 'Bạn có yêu cầu đặt mua mới',
            'Có người muốn mua "' || v_product.title || '" với giá ' || v_price || 'đ',
            v_order_id);

    return query select * from v_orders where id = v_order_id;
end;
$$;

create or replace function api_update_order(
    p_token text,
    p_order_id bigint,
    p_status text,
    p_note text default null
)
returns setof v_orders
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_uid bigint := _session_user(p_token);
    o order_requests;
    v_target text := upper(trim(p_status));
begin
    select * into o from order_requests where id = p_order_id;
    if o.id is null then raise exception 'ORDER_NOT_FOUND'; end if;
    if v_uid not in (o.buyer_id, o.seller_id) then raise exception 'NOT_PARTICIPANT'; end if;

    if v_target = 'ACCEPTED' then
        if o.status <> 'PENDING' then raise exception 'INVALID_TRANSITION'; end if;
        update order_requests set status = 'ACCEPTED', confirmed_at = now(), status_updated_by = v_uid,
               seller_note = coalesce(p_note, seller_note), updated_at = now()
        where id = p_order_id;
        update products set status = 'IN_TRANSACTION' where id = o.product_id;

    elsif v_target = 'COMPLETED' then
        if o.status <> 'ACCEPTED' then raise exception 'INVALID_TRANSITION'; end if;
        update order_requests set status = 'COMPLETED', payment_status = 'COMPLETED',
               completed_at = now(), status_updated_by = v_uid, updated_at = now()
        where id = p_order_id;
        update products set status = 'SOLD' where id = o.product_id;
        update users set completed_deals = completed_deals + 1 where id = o.seller_id;
        if not exists (select 1 from transactions where order_id = p_order_id) then
            insert into transactions(order_id, buyer_id, seller_id, product_id, transaction_amount,
                                     platform_fee_rate, platform_fee_amount, seller_receive_amount)
            values (p_order_id, o.buyer_id, o.seller_id, o.product_id, o.offered_price,
                    o.platform_fee_rate, o.platform_fee_amount, o.final_seller_amount);
        end if;

    elsif v_target in ('REJECTED', 'CANCELLED', 'DISPUTED') then
        update order_requests set
            status = v_target,
            cancel_reason = case when v_target in ('REJECTED','CANCELLED') then p_note else cancel_reason end,
            dispute_reason = case when v_target = 'DISPUTED' then p_note else dispute_reason end,
            status_updated_by = v_uid,
            updated_at = now()
        where id = p_order_id;
        if v_target in ('REJECTED', 'CANCELLED') then
            update products set status = 'APPROVED'
            where id = o.product_id and status = 'IN_TRANSACTION';
        end if;
    else
        raise exception 'INVALID_STATUS';
    end if;

    return query select * from v_orders where id = p_order_id;
end;
$$;

-- =================================================================================
-- 7. CHAT
-- =================================================================================
create or replace function api_messages(p_token text, p_partner_id bigint)
returns setof v_chat_messages
language sql
security definer
set search_path = public, extensions
as $$
    select * from v_chat_messages
    where (sender_id = _session_user(p_token) and receiver_id = p_partner_id)
       or (sender_id = p_partner_id and receiver_id = _session_user(p_token))
    order by created_at asc;
$$;

create or replace function api_send_message(
    p_token text,
    p_receiver_id bigint,
    p_product_id bigint default null,
    p_content text default ''
)
returns setof v_chat_messages
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_sender bigint := _session_user(p_token);
    v_id bigint;
begin
    if p_content is null or length(trim(p_content)) = 0 then raise exception 'EMPTY_MESSAGE'; end if;

    insert into chat_messages(sender_id, receiver_id, product_id, content, message_type)
    values (v_sender, p_receiver_id, p_product_id, trim(p_content), 'TEXT')
    returning id into v_id;

    return query select * from v_chat_messages where id = v_id;
end;
$$;

-- =================================================================================
-- 8. QUYỀN TRUY CẬP CHO ANON (app mobile)
-- =================================================================================
-- Thu hồi mọi quyền trực tiếp trên bảng/view nội bộ, sau đó chỉ mở đúng phần cần thiết.
revoke all on all tables in schema public from anon, authenticated;

-- Bật RLS để phòng thủ nhiều lớp (RPC chạy bằng quyền owner nên vẫn hoạt động)
do $$
declare t text;
begin
    for t in
        select unnest(array[
            'users','products','product_images','order_requests','transactions',
            'platform_settings','platform_fee_tiers','wishlists','chat_messages',
            'notifications','reviews','reports','moderation_logs','app_sessions'
        ])
    loop
        execute format('alter table %I enable row level security', t);
    end loop;
end $$;

grant usage on schema public to anon, authenticated;

-- Chỉ đọc dữ liệu công khai
grant select on categories, universities, v_public_products to anon, authenticated;

-- Không cấp quyền trên bảng gốc: mọi ghi/đọc riêng tư đi qua RPC
grant execute on function api_login(text, text) to anon, authenticated;
grant execute on function api_register(text, text, text, text, text, bigint, text) to anon, authenticated;
grant execute on function api_logout(text) to anon, authenticated;
grant execute on function api_me(text) to anon, authenticated;
grant execute on function api_update_profile(text, text, text, text, text, text) to anon, authenticated;
grant execute on function api_my_posts(text) to anon, authenticated;
grant execute on function api_create_product(text, text, text, numeric, boolean, boolean, text, text, text, bigint, bigint, text[]) to anon, authenticated;
grant execute on function api_delete_product(text, bigint) to anon, authenticated;
grant execute on function api_my_orders(text) to anon, authenticated;
grant execute on function api_my_wishlist(text) to anon, authenticated;
grant execute on function api_toggle_wishlist(text, bigint) to anon, authenticated;
grant execute on function api_place_order(text, bigint, numeric, text, text, text) to anon, authenticated;
grant execute on function api_update_order(text, bigint, text, text) to anon, authenticated;
grant execute on function api_messages(text, bigint) to anon, authenticated;
grant execute on function api_send_message(text, bigint, bigint, text) to anon, authenticated;
grant execute on function api_fee_preview(numeric) to anon, authenticated;

-- =================================================================================
-- 9. STORAGE (ảnh sản phẩm / avatar) — chạy trong SQL Editor
-- =================================================================================
insert into storage.buckets (id, name, public)
values ('passuni', 'passuni', true)
on conflict (id) do nothing;

drop policy if exists "passuni public read" on storage.objects;
create policy "passuni public read" on storage.objects
    for select to anon, authenticated using (bucket_id = 'passuni');

drop policy if exists "passuni anon upload" on storage.objects;
create policy "passuni anon upload" on storage.objects
    for insert to anon, authenticated with check (bucket_id = 'passuni');

-- =================================================================================
-- KIỂM TRA
-- =================================================================================
select 'v_public_products' as item, count(*) as rows from v_public_products
union all select 'v_user_profiles', count(*) from v_user_profiles
union all select 'app_sessions', count(*) from app_sessions;
