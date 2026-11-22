-- =====================================================================
-- NẾP NHÀ – Giai đoạn 2: RPC phục vụ màn hình Gia tộc trong ứng dụng
-- =====================================================================

-- Gia tộc của tôi (kể cả đang chờ duyệt / bị từ chối) – chỉ trả về tên, mã, trạng thái của chính mình.
create or replace function public.my_memberships()
returns table(member_id uuid, clan_id uuid, clan_name text, clan_code text, hometown text,
              status member_status, role clan_role, branch_id uuid, admin_note text, requested_at timestamptz)
language sql stable security definer set search_path = public, extensions as $$
  select m.id, c.id, c.name,
         case when m.status = 'active' then c.code else null end,
         case when m.status = 'active' then c.hometown else null end,
         m.status, m.role, m.branch_id,
         m.admin_note, m.requested_at
  from public.clan_members m join public.clans c on c.id = m.clan_id
  where m.user_id = auth.uid() and c.deleted_at is null and m.status <> 'left'
  order by (m.status = 'active') desc, c.name
$$;

-- Xem trước mã mời trước khi gửi yêu cầu: chỉ tên gia tộc, quê quán và danh sách tên chi (để chọn nhánh).
-- Không tiết lộ thành viên hay gia phả. Có giới hạn số lần thử giống request_join.
create or replace function public.preview_invite(p_code text)
returns table(clan_name text, hometown text, branches jsonb)
language plpgsql security definer set search_path = public, extensions as $$
declare i public.invite_codes;
begin
  if auth.uid() is null then raise exception 'Chưa đăng nhập' using errcode = '28000'; end if;
  if (select count(*) from public.audit_log where actor_id = auth.uid() and action = 'join_attempt'
      and at > now() - interval '1 hour') >= 10 then
    raise exception 'Thử quá nhiều lần, vui lòng đợi' using errcode = '54000';
  end if;
  insert into public.audit_log(actor_id, action, entity) values (auth.uid(), 'join_attempt', 'invite_codes');
  select * into i from public.invite_codes where code_hash = encode(digest(upper(trim(p_code)), 'sha256'), 'hex');
  if i.id is null or i.revoked_at is not null or (i.expires_at is not null and i.expires_at < now())
     or (i.max_uses is not null and i.used_count >= i.max_uses) then
    raise exception 'Mã mời không hợp lệ hoặc đã hết hạn' using errcode = '22023';
  end if;
  return query
    select c.name, c.hometown,
      coalesce((select jsonb_agg(jsonb_build_object('id', b.id, 'name', b.name) order by b.order_index, b.name)
                from public.clan_branches b
                where b.clan_id = c.id and b.deleted_at is null
                  and (i.branch_id is null or public.branch_within(b.id, i.branch_id))), '[]'::jsonb)
    from public.clans c where c.id = i.clan_id;
end $$;

-- Danh sách thành viên kèm tên hiển thị.
--  * Thành viên đã duyệt: thấy thành viên đang hoạt động.
--  * Người quản lý thành viên: thấy thêm yêu cầu chờ duyệt / cần bổ sung / bị khóa trong phạm vi của mình.
create or replace function public.clan_member_list(p_clan uuid)
returns table(member_id uuid, user_id uuid, display_name text, claimed_name text, status member_status,
              role clan_role, branch_id uuid, branch_name text, join_note text, related_relation text,
              admin_note text, requested_at timestamptz, decided_at timestamptz)
language plpgsql stable security definer set search_path = public, extensions as $$
begin
  if not public.is_active_member(p_clan) then
    raise exception 'Bạn chưa là thành viên của gia tộc này' using errcode = '42501';
  end if;
  return query
    select m.id, m.user_id, coalesce(nullif(p.display_name, ''), m.claimed_name, 'Thành viên'), m.claimed_name,
           m.status, m.role, m.branch_id, b.name, m.join_note, m.related_relation, m.admin_note,
           m.requested_at, m.decided_at
    from public.clan_members m
    left join public.profiles p on p.id = m.user_id
    left join public.clan_branches b on b.id = m.branch_id
    where m.clan_id = p_clan and m.status <> 'left'
      and (m.status = 'active' or public.can_manage_members(p_clan, m.branch_id))
    order by case m.status when 'pending' then 0 when 'needs_info' then 1 when 'active' then 2 else 3 end,
             public.role_rank(m.role) desc, m.requested_at;
end $$;

-- Lịch sử hoạt động có tên người thao tác (chỉ quản trị trở lên), phân trang.
create or replace function public.clan_activity(p_clan uuid, p_before timestamptz default null, p_limit int default 50)
returns table(id bigint, at timestamptz, actor_name text, action text, entity text)
language plpgsql stable security definer set search_path = public, extensions as $$
begin
  if public.my_rank(p_clan) < public.role_rank('admin') then
    raise exception 'Không có quyền xem lịch sử' using errcode = '42501';
  end if;
  return query
    select a.id, a.at, coalesce(nullif(p.display_name, ''), 'Hệ thống'), a.action, a.entity
    from public.audit_log a left join public.profiles p on p.id = a.actor_id
    where a.clan_id = p_clan and (p_before is null or a.at < p_before)
    order by a.at desc
    limit least(greatest(p_limit, 1), 200);
end $$;

-- Cập nhật tên hiển thị của chính mình
create or replace function public.set_display_name(p_name text) returns void
language plpgsql security definer set search_path = public, extensions as $$
begin
  if auth.uid() is null then raise exception 'Chưa đăng nhập' using errcode = '28000'; end if;
  insert into public.profiles(id, display_name) values (auth.uid(), left(trim(p_name), 80))
  on conflict (id) do update set display_name = excluded.display_name, updated_at = now();
end $$;

revoke all on function public.my_memberships() from public, anon;
revoke all on function public.preview_invite(text) from public, anon;
revoke all on function public.clan_member_list(uuid) from public, anon;
revoke all on function public.clan_activity(uuid, timestamptz, int) from public, anon;
revoke all on function public.set_display_name(text) from public, anon;
grant execute on function public.my_memberships() to authenticated;
grant execute on function public.preview_invite(text) to authenticated;
grant execute on function public.clan_member_list(uuid) to authenticated;
grant execute on function public.clan_activity(uuid, timestamptz, int) to authenticated;
grant execute on function public.set_display_name(text) to authenticated;
