-- =====================================================================
-- NẾP NHÀ – Lược đồ cơ sở dữ liệu gia tộc trực tuyến (Supabase / PostgreSQL 15+)
-- Nguyên tắc:
--   * Tách tài khoản đăng nhập (auth.users / profiles) khỏi hồ sơ người trong gia phả (persons).
--   * Chỉ lưu quan hệ gốc (cha/mẹ–con, hôn nhân); vai vế được suy ra ở ứng dụng.
--   * Mọi quyền được kiểm tra ở backend bằng RLS + hàm SECURITY DEFINER. Ứng dụng chỉ dùng anon key.
--   * Mỗi bản ghi có version để chống ghi đè âm thầm (optimistic concurrency).
--   * Audit log cho thao tác quan trọng.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- kiểu dữ liệu
do $$ begin
  create type clan_role as enum (
    'owner',              -- Chủ gia tộc
    'patriarch',          -- Trưởng tộc
    'vice_patriarch',     -- Phó trưởng tộc
    'admin',              -- Quản trị viên
    'branch_head',        -- Trưởng chi (chỉ trong phạm vi branch_id)
    'genealogy_manager',  -- Người quản lý gia phả
    'member',             -- Thành viên
    'guest'               -- Khách được mời
  );
exception when duplicate_object then null; end $$;

do $$ begin
  create type member_status as enum ('pending', 'needs_info', 'active', 'rejected', 'locked', 'left');
exception when duplicate_object then null; end $$;

do $$ begin
  create type parent_type as enum ('biological', 'adoptive');
exception when duplicate_object then null; end $$;

do $$ begin
  create type gender as enum ('male', 'female', 'unknown');
exception when duplicate_object then null; end $$;

-- Thứ hạng quyền (lớn hơn = quyền cao hơn). Độc lập với vai vế huyết thống.
create or replace function public.role_rank(r clan_role) returns int
language sql immutable as $$
  select case r
    when 'owner' then 100 when 'patriarch' then 90 when 'vice_patriarch' then 80
    when 'admin' then 70 when 'branch_head' then 50 when 'genealogy_manager' then 40
    when 'member' then 10 else 1 end
$$;

-- ---------------------------------------------------------------- bảng
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  phone text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.clans (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,                -- Mã định danh gia tộc (công khai trong nội bộ, KHÔNG phải mã mời)
  name text not null check (char_length(name) between 2 and 120),
  avatar_path text,                          -- đường dẫn trong Storage bucket "clan-media" (private)
  hometown text,
  description text,
  owner_id uuid not null references auth.users(id),
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- Chi / nhánh. order_index là THỨ TỰ CHI (chi trưởng = 1), KHÔNG phải thế hệ gia phả.
create table if not exists public.clan_branches (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  parent_branch_id uuid references public.clan_branches(id) on delete set null,
  name text not null,
  order_index int not null default 1,
  head_user_id uuid references auth.users(id),
  founder_person_id uuid,                    -- người khởi lập chi (persons.id)
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists clan_branches_clan_idx on public.clan_branches(clan_id);

create table if not exists public.clan_members (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  status member_status not null default 'pending',
  role clan_role not null default 'member',
  branch_id uuid references public.clan_branches(id) on delete set null, -- phạm vi quản lý (trưởng chi) / nhánh khai báo
  person_id uuid,                            -- hồ sơ của chính mình trong gia phả (nếu đã liên kết)
  claimed_name text,
  join_note text,
  related_person_id uuid,                    -- người thân liên quan khai báo khi xin vào
  related_relation text,
  admin_note text,
  decided_by uuid references auth.users(id),
  decided_at timestamptz,
  requested_at timestamptz not null default now(),
  version int not null default 1,
  updated_at timestamptz not null default now(),
  unique (clan_id, user_id)
);
create index if not exists clan_members_user_idx on public.clan_members(user_id);
create index if not exists clan_members_clan_status_idx on public.clan_members(clan_id, status);

-- Mã mời: chỉ lưu băm SHA-256, không lưu mã gốc. Có hạn dùng, số lần dùng và thu hồi.
create table if not exists public.invite_codes (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  branch_id uuid references public.clan_branches(id) on delete cascade,
  code_hash text not null unique,
  code_hint text not null,                   -- 2 ký tự cuối để quản trị nhận diện
  default_role clan_role not null default 'member',
  created_by uuid not null references auth.users(id),
  expires_at timestamptz,
  max_uses int,
  used_count int not null default 0,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.persons (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  branch_id uuid references public.clan_branches(id) on delete set null,
  full_name text not null check (char_length(full_name) between 1 and 120),
  other_names text,
  gender gender not null default 'unknown',
  birth_date date,
  birth_year int,
  birth_order int check (birth_order is null or birth_order > 0),
  is_deceased boolean not null default false,
  death_date date,
  death_lunar_day int check (death_lunar_day between 1 and 30),
  death_lunar_month int check (death_lunar_month between 1 and 12),
  death_lunar_year int,
  death_lunar_leap boolean not null default false,
  burial_place text,
  photo_path text,
  biography text,
  privacy text not null default 'clan' check (privacy in ('clan', 'branch', 'managers')),
  linked_user_id uuid references auth.users(id),
  created_by uuid references auth.users(id),
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists persons_clan_updated_idx on public.persons(clan_id, updated_at);
create index if not exists persons_clan_name_idx on public.persons(clan_id, full_name);

create table if not exists public.parent_links (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  child_id uuid not null references public.persons(id) on delete cascade,
  parent_id uuid not null references public.persons(id) on delete cascade,
  type parent_type not null default 'biological',
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (child_id <> parent_id),
  unique (child_id, parent_id)
);
create index if not exists parent_links_parent_idx on public.parent_links(parent_id);
create index if not exists parent_links_clan_updated_idx on public.parent_links(clan_id, updated_at);

create table if not exists public.marriages (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  a_id uuid not null references public.persons(id) on delete cascade,
  b_id uuid not null references public.persons(id) on delete cascade,
  status text not null default 'current' check (status in ('current', 'divorced', 'widowed')),
  order_index int,                           -- vợ cả / vợ hai...
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (a_id <> b_id)
);
create index if not exists marriages_clan_updated_idx on public.marriages(clan_id, updated_at);

-- Xác nhận thứ bậc khi thiếu ngày sinh / thứ tự (dùng cho suy vai vế)
create table if not exists public.seniority_confirmations (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  older_id uuid not null references public.persons(id) on delete cascade,
  younger_id uuid not null references public.persons(id) on delete cascade,
  confirmed_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  unique (older_id, younger_id),
  check (older_id <> younger_id)
);

-- Cách xưng hô tùy chỉnh theo phong tục gia đình
create table if not exists public.kinship_overrides (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  viewer_person_id uuid not null references public.persons(id) on delete cascade,
  target_person_id uuid not null references public.persons(id) on delete cascade,
  term text not null check (char_length(term) between 1 and 40),
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  unique (viewer_person_id, target_person_id)
);

create table if not exists public.clan_events (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  branch_id uuid references public.clan_branches(id) on delete set null,
  person_id uuid references public.persons(id) on delete set null,
  type text not null check (type in ('death_anniversary','birthday','anniversary','holiday','wedding','gathering','other')),
  title text not null,
  calendar_kind text not null check (calendar_kind in ('solar', 'lunar')),
  day int not null check (day between 1 and 31),
  month int not null check (month between 1 and 12),
  year int not null,
  leap_month boolean not null default false,
  repeat text not null default 'yearly' check (repeat in ('none','yearly','monthly','every_n_days')),
  interval_days int not null default 1,
  hour int not null default 8 check (hour between 0 and 23),
  minute int not null default 0 check (minute between 0 and 59),
  location text,
  note text,
  created_by uuid references auth.users(id),
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists clan_events_clan_updated_idx on public.clan_events(clan_id, updated_at);

create table if not exists public.clan_tasks (
  id uuid primary key default gen_random_uuid(),
  clan_id uuid not null references public.clans(id) on delete cascade,
  branch_id uuid references public.clan_branches(id) on delete set null,
  event_id uuid references public.clan_events(id) on delete set null,
  title text not null,
  assignee_user_id uuid references auth.users(id),
  due_date date,
  status text not null default 'todo' check (status in ('todo','doing','done')),
  checklist jsonb not null default '[]'::jsonb,
  created_by uuid references auth.users(id),
  version int not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists clan_tasks_clan_updated_idx on public.clan_tasks(clan_id, updated_at);

create table if not exists public.audit_log (
  id bigint generated always as identity primary key,
  clan_id uuid references public.clans(id) on delete cascade,
  actor_id uuid,
  action text not null,
  entity text not null,
  entity_id uuid,
  before jsonb,
  after jsonb,
  at timestamptz not null default now()
);
create index if not exists audit_log_clan_at_idx on public.audit_log(clan_id, at desc);

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  fcm_token text not null unique,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);

create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  clan_id uuid references public.clans(id) on delete cascade,
  kind text not null,
  title text not null,
  body text,
  payload jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists notifications_user_idx on public.notifications(user_id, created_at desc);

-- ---------------------------------------------------------------- hàm kiểm tra quyền
create or replace function public.my_membership(p_clan uuid)
returns public.clan_members
language sql stable security definer set search_path = public, extensions as $$
  select * from public.clan_members
  where clan_id = p_clan and user_id = auth.uid() and status = 'active'
  limit 1
$$;

create or replace function public.is_active_member(p_clan uuid) returns boolean
language sql stable security definer set search_path = public, extensions as $$
  select exists (select 1 from public.clan_members
                 where clan_id = p_clan and user_id = auth.uid() and status = 'active')
$$;

create or replace function public.my_rank(p_clan uuid) returns int
language sql stable security definer set search_path = public, extensions as $$
  select coalesce((select public.role_rank(role) from public.clan_members
                   where clan_id = p_clan and user_id = auth.uid() and status = 'active'), 0)
$$;

-- Nhánh p_branch có nằm trong nhánh p_scope (kể cả nhánh con) không
create or replace function public.branch_within(p_branch uuid, p_scope uuid) returns boolean
language sql stable security definer set search_path = public, extensions as $$
  with recursive up as (
    select id, parent_branch_id, 0 as d from public.clan_branches where id = p_branch
    union all
    select b.id, b.parent_branch_id, up.d + 1 from public.clan_branches b join up on b.id = up.parent_branch_id
    where up.d < 32
  )
  select p_scope is not null and exists (select 1 from up where id = p_scope)
$$;

-- Quản lý thành viên / xét duyệt: admin trở lên toàn tộc; trưởng chi trong phạm vi chi
create or replace function public.can_manage_members(p_clan uuid, p_branch uuid) returns boolean
language plpgsql stable security definer set search_path = public, extensions as $$
declare m public.clan_members;
begin
  m := public.my_membership(p_clan);
  if m.id is null then return false; end if;
  if public.role_rank(m.role) >= public.role_rank('admin') then return true; end if;
  if m.role = 'branch_head' and p_branch is not null and public.branch_within(p_branch, m.branch_id) then
    return true;
  end if;
  return false;
end $$;

-- Sửa gia phả / sự kiện: admin trở lên, người quản lý gia phả; trưởng chi trong phạm vi chi
create or replace function public.can_edit_clan_data(p_clan uuid, p_branch uuid) returns boolean
language plpgsql stable security definer set search_path = public, extensions as $$
declare m public.clan_members;
begin
  m := public.my_membership(p_clan);
  if m.id is null then return false; end if;
  if public.role_rank(m.role) >= public.role_rank('admin') or m.role = 'genealogy_manager' then
    return true;
  end if;
  if m.role = 'branch_head' and p_branch is not null and public.branch_within(p_branch, m.branch_id) then
    return true;
  end if;
  return false;
end $$;

-- Xem hồ sơ theo mức riêng tư
create or replace function public.can_view_person(p_clan uuid, p_branch uuid, p_privacy text) returns boolean
language plpgsql stable security definer set search_path = public, extensions as $$
declare m public.clan_members;
begin
  m := public.my_membership(p_clan);
  if m.id is null then return false; end if;
  if m.role = 'guest' and p_privacy <> 'clan' then return false; end if;
  if p_privacy = 'clan' then return true; end if;
  if public.can_edit_clan_data(p_clan, p_branch) then return true; end if;
  if p_privacy = 'branch' then
    return p_branch is not null and m.branch_id is not null and public.branch_within(p_branch, m.branch_id);
  end if;
  return false;
end $$;

-- ---------------------------------------------------------------- trigger: version, updated_at, chống vòng lặp
create or replace function public.bump_version() returns trigger
language plpgsql as $$
begin
  new.version := old.version + 1;
  new.updated_at := now();
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['clans','clan_branches','clan_members','persons','parent_links','marriages','clan_events','clan_tasks'] loop
    execute format('drop trigger if exists trg_bump_version on public.%I', t);
    execute format('create trigger trg_bump_version before update on public.%I for each row execute function public.bump_version()', t);
  end loop;
end $$;

-- Không cho tạo quan hệ cha mẹ – con tạo thành vòng (một người là tổ tiên của chính mình)
create or replace function public.prevent_parent_cycle() returns trigger
language plpgsql as $$
declare found boolean;
begin
  if new.deleted_at is not null then return new; end if;
  -- cả hai người phải cùng gia tộc
  if not exists (select 1 from public.persons where id = new.child_id and clan_id = new.clan_id)
     or not exists (select 1 from public.persons where id = new.parent_id and clan_id = new.clan_id) then
    raise exception 'Hai hồ sơ không thuộc cùng gia tộc' using errcode = '22023';
  end if;
  with recursive anc as (
    select parent_id, 1 as d from public.parent_links where child_id = new.parent_id and deleted_at is null
    union
    select pl.parent_id, anc.d + 1 from public.parent_links pl join anc on pl.child_id = anc.parent_id
    where pl.deleted_at is null and anc.d < 64
  )
  select exists (select 1 from anc where parent_id = new.child_id) into found;
  if found or new.parent_id = new.child_id then
    raise exception 'Quan hệ tạo vòng lặp trong gia phả' using errcode = '22023';
  end if;
  -- tối đa 2 cha mẹ ruột
  if new.type = 'biological' and (
      select count(*) from public.parent_links
      where child_id = new.child_id and type = 'biological' and deleted_at is null and id <> new.id) >= 2 then
    raise exception 'Một người chỉ có tối đa 2 cha mẹ ruột' using errcode = '22023';
  end if;
  return new;
end $$;

drop trigger if exists trg_parent_cycle on public.parent_links;
create trigger trg_parent_cycle before insert or update on public.parent_links
for each row execute function public.prevent_parent_cycle();

-- ---------------------------------------------------------------- audit
create or replace function public.audit_row() returns trigger
language plpgsql security definer set search_path = public, extensions as $$
declare v_clan uuid; v_id uuid;
begin
  if tg_op = 'DELETE' then
    v_clan := old.clan_id; v_id := old.id;
  else
    v_clan := new.clan_id; v_id := new.id;
  end if;
  insert into public.audit_log(clan_id, actor_id, action, entity, entity_id, before, after)
  values (v_clan, auth.uid(), lower(tg_op), tg_table_name, v_id,
          case when tg_op <> 'INSERT' then to_jsonb(old) end,
          case when tg_op <> 'DELETE' then to_jsonb(new) end);
  return coalesce(new, old);
end $$;

do $$
declare t text;
begin
  foreach t in array array['clan_branches','clan_members','persons','parent_links','marriages','clan_events','seniority_confirmations','kinship_overrides'] loop
    execute format('drop trigger if exists trg_audit on public.%I', t);
    execute format('create trigger trg_audit after insert or update or delete on public.%I for each row execute function public.audit_row()', t);
  end loop;
end $$;

-- ---------------------------------------------------------------- RLS
alter table public.profiles enable row level security;
alter table public.clans enable row level security;
alter table public.clan_branches enable row level security;
alter table public.clan_members enable row level security;
alter table public.invite_codes enable row level security;
alter table public.persons enable row level security;
alter table public.parent_links enable row level security;
alter table public.marriages enable row level security;
alter table public.seniority_confirmations enable row level security;
alter table public.kinship_overrides enable row level security;
alter table public.clan_events enable row level security;
alter table public.clan_tasks enable row level security;
alter table public.audit_log enable row level security;
alter table public.devices enable row level security;
alter table public.notifications enable row level security;

-- profiles: tự xem/sửa của mình; thành viên cùng gia tộc xem tên hiển thị
drop policy if exists profiles_self on public.profiles;
create policy profiles_self on public.profiles for all using (id = auth.uid()) with check (id = auth.uid());
drop policy if exists profiles_clanmates on public.profiles;
create policy profiles_clanmates on public.profiles for select using (
  exists (select 1 from public.clan_members a join public.clan_members b on a.clan_id = b.clan_id
          where a.user_id = auth.uid() and a.status = 'active' and b.user_id = profiles.id and b.status = 'active'));

-- clans: chỉ thành viên đã duyệt được đọc. Tạo qua RPC create_clan. Sửa: admin trở lên.
drop policy if exists clans_select on public.clans;
create policy clans_select on public.clans for select using (deleted_at is null and public.is_active_member(id));
drop policy if exists clans_update on public.clans;
create policy clans_update on public.clans for update
  using (public.my_rank(id) >= public.role_rank('admin'))
  with check (public.my_rank(id) >= public.role_rank('admin'));

-- branches
drop policy if exists branches_select on public.clan_branches;
create policy branches_select on public.clan_branches for select using (public.is_active_member(clan_id));
drop policy if exists branches_write on public.clan_branches;
create policy branches_write on public.clan_branches for all
  using (public.my_rank(clan_id) >= public.role_rank('admin'))
  with check (public.my_rank(clan_id) >= public.role_rank('admin'));

-- members: tự xem yêu cầu của mình; thành viên đã duyệt xem danh sách thành viên đang hoạt động;
-- người có quyền quản lý xem tất cả (kể cả đang chờ duyệt). Ghi chỉ qua RPC.
drop policy if exists members_select on public.clan_members;
create policy members_select on public.clan_members for select using (
  user_id = auth.uid()
  or (status = 'active' and public.is_active_member(clan_id))
  or public.can_manage_members(clan_id, branch_id));

-- invite codes: chỉ người quản lý thành viên được xem (mã gốc không bao giờ được lưu)
drop policy if exists invites_select on public.invite_codes;
create policy invites_select on public.invite_codes for select using (public.can_manage_members(clan_id, branch_id));

-- persons
drop policy if exists persons_select on public.persons;
create policy persons_select on public.persons for select using (public.can_view_person(clan_id, branch_id, privacy));
drop policy if exists persons_insert on public.persons;
create policy persons_insert on public.persons for insert with check (public.can_edit_clan_data(clan_id, branch_id));
drop policy if exists persons_update on public.persons;
create policy persons_update on public.persons for update
  using (public.can_edit_clan_data(clan_id, branch_id))
  with check (public.can_edit_clan_data(clan_id, branch_id));
-- Không có policy DELETE: xóa mềm bằng deleted_at để giữ lịch sử.

-- quan hệ
drop policy if exists links_select on public.parent_links;
create policy links_select on public.parent_links for select using (public.is_active_member(clan_id));
drop policy if exists links_write on public.parent_links;
create policy links_write on public.parent_links for insert with check (
  public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = child_id)));
drop policy if exists links_update on public.parent_links;
create policy links_update on public.parent_links for update
  using (public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = child_id)))
  with check (public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = child_id)));

drop policy if exists marriages_select on public.marriages;
create policy marriages_select on public.marriages for select using (public.is_active_member(clan_id));
drop policy if exists marriages_insert on public.marriages;
create policy marriages_insert on public.marriages for insert with check (public.can_edit_clan_data(clan_id, null)
  or public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = a_id)));
drop policy if exists marriages_update on public.marriages;
create policy marriages_update on public.marriages for update
  using (public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = a_id)))
  with check (public.can_edit_clan_data(clan_id, (select branch_id from public.persons where id = a_id)));

drop policy if exists seniority_select on public.seniority_confirmations;
create policy seniority_select on public.seniority_confirmations for select using (public.is_active_member(clan_id));
drop policy if exists seniority_write on public.seniority_confirmations;
create policy seniority_write on public.seniority_confirmations for insert
  with check (confirmed_by = auth.uid() and public.can_edit_clan_data(clan_id, null));

drop policy if exists overrides_select on public.kinship_overrides;
create policy overrides_select on public.kinship_overrides for select using (public.is_active_member(clan_id));
drop policy if exists overrides_write on public.kinship_overrides;
create policy overrides_write on public.kinship_overrides for insert
  with check (created_by = auth.uid() and public.is_active_member(clan_id));

-- sự kiện, công việc
drop policy if exists events_select on public.clan_events;
create policy events_select on public.clan_events for select using (public.is_active_member(clan_id));
drop policy if exists events_insert on public.clan_events;
create policy events_insert on public.clan_events for insert with check (public.can_edit_clan_data(clan_id, branch_id));
drop policy if exists events_update on public.clan_events;
create policy events_update on public.clan_events for update
  using (public.can_edit_clan_data(clan_id, branch_id)) with check (public.can_edit_clan_data(clan_id, branch_id));

drop policy if exists tasks_select on public.clan_tasks;
create policy tasks_select on public.clan_tasks for select using (public.is_active_member(clan_id));
drop policy if exists tasks_insert on public.clan_tasks;
create policy tasks_insert on public.clan_tasks for insert with check (public.can_edit_clan_data(clan_id, branch_id));
drop policy if exists tasks_update on public.clan_tasks;
create policy tasks_update on public.clan_tasks for update using (
  public.can_edit_clan_data(clan_id, branch_id) or assignee_user_id = auth.uid())
  with check (public.can_edit_clan_data(clan_id, branch_id) or assignee_user_id = auth.uid());

-- audit: chỉ admin trở lên đọc; không ai sửa/xóa qua API
drop policy if exists audit_select on public.audit_log;
create policy audit_select on public.audit_log for select using (public.my_rank(clan_id) >= public.role_rank('admin'));

drop policy if exists devices_self on public.devices;
create policy devices_self on public.devices for all using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists notifications_self on public.notifications;
create policy notifications_self on public.notifications for select using (user_id = auth.uid());
drop policy if exists notifications_mark_read on public.notifications;
create policy notifications_mark_read on public.notifications for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------------------------------------------------------------- RPC nghiệp vụ
-- Bảng chữ không gây nhầm lẫn (bỏ 0/O, 1/I/L)
create or replace function public.random_code(len int) returns text
language plpgsql volatile set search_path = public, extensions as $$
declare alphabet text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; out text := ''; b bytea; i int;
begin
  b := gen_random_bytes(len);
  for i in 0..len - 1 loop
    out := out || substr(alphabet, (get_byte(b, i) % length(alphabet)) + 1, 1);
  end loop;
  return out;
end $$;

create or replace function public.create_clan(p_name text, p_hometown text default null, p_description text default null)
returns public.clans
language plpgsql security definer set search_path = public, extensions as $$
declare c public.clans; v_code text; tries int := 0;
begin
  if auth.uid() is null then raise exception 'Chưa đăng nhập' using errcode = '28000'; end if;
  if (select count(*) from public.clans where owner_id = auth.uid() and created_at > now() - interval '1 day') >= 5 then
    raise exception 'Tạo quá nhiều gia tộc trong 24 giờ' using errcode = '54000';
  end if;
  loop
    v_code := 'NN-' || public.random_code(6);
    exit when not exists (select 1 from public.clans where code = v_code);
    tries := tries + 1;
    if tries > 10 then raise exception 'Không cấp được mã định danh'; end if;
  end loop;
  insert into public.clans(code, name, hometown, description, owner_id)
  values (v_code, trim(p_name), p_hometown, p_description, auth.uid()) returning * into c;
  insert into public.clan_members(clan_id, user_id, status, role, decided_by, decided_at)
  values (c.id, auth.uid(), 'active', 'owner', auth.uid(), now());
  insert into public.audit_log(clan_id, actor_id, action, entity, entity_id, after)
  values (c.id, auth.uid(), 'create', 'clans', c.id, to_jsonb(c));
  return c;
end $$;

-- Trả về mã mời gốc DUY NHẤT một lần; DB chỉ lưu băm.
create or replace function public.create_invite(
  p_clan uuid, p_branch uuid default null, p_expires_hours int default 168, p_max_uses int default 20)
returns table(invite_id uuid, code text, expires_at timestamptz)
language plpgsql security definer set search_path = public, extensions as $$
declare v_code text; v_id uuid; v_exp timestamptz;
begin
  if not public.can_manage_members(p_clan, p_branch) then
    raise exception 'Không có quyền tạo mã mời' using errcode = '42501';
  end if;
  v_code := public.random_code(4) || '-' || public.random_code(4);
  v_exp := case when p_expires_hours is null then null else now() + make_interval(hours => p_expires_hours) end;
  insert into public.invite_codes(clan_id, branch_id, code_hash, code_hint, created_by, expires_at, max_uses)
  values (p_clan, p_branch, encode(digest(v_code, 'sha256'), 'hex'), right(v_code, 2), auth.uid(), v_exp, p_max_uses)
  returning id into v_id;
  insert into public.audit_log(clan_id, actor_id, action, entity, entity_id)
  values (p_clan, auth.uid(), 'create_invite', 'invite_codes', v_id);
  return query select v_id, v_code, v_exp;
end $$;

create or replace function public.revoke_invite(p_invite uuid) returns void
language plpgsql security definer set search_path = public, extensions as $$
declare i public.invite_codes;
begin
  select * into i from public.invite_codes where id = p_invite;
  if i.id is null or not public.can_manage_members(i.clan_id, i.branch_id) then
    raise exception 'Không có quyền' using errcode = '42501';
  end if;
  update public.invite_codes set revoked_at = now() where id = p_invite;
  insert into public.audit_log(clan_id, actor_id, action, entity, entity_id)
  values (i.clan_id, auth.uid(), 'revoke_invite', 'invite_codes', p_invite);
end $$;

-- Xin gia nhập bằng mã mời. Không trả về dữ liệu riêng tư nào của gia tộc ngoài tên.
create or replace function public.request_join(
  p_code text, p_claimed_name text, p_note text default null,
  p_branch uuid default null, p_related_person uuid default null, p_related_relation text default null)
returns table(member_id uuid, clan_name text, status member_status)
language plpgsql security definer set search_path = public, extensions as $$
declare i public.invite_codes; v_hash text; m public.clan_members; v_name text;
begin
  if auth.uid() is null then raise exception 'Chưa đăng nhập' using errcode = '28000'; end if;
  -- chống dò mã: tối đa 10 lần thử mỗi giờ
  if (select count(*) from public.audit_log where actor_id = auth.uid() and action = 'join_attempt'
      and at > now() - interval '1 hour') >= 10 then
    raise exception 'Thử quá nhiều lần, vui lòng đợi' using errcode = '54000';
  end if;
  insert into public.audit_log(actor_id, action, entity) values (auth.uid(), 'join_attempt', 'invite_codes');

  v_hash := encode(digest(upper(trim(p_code)), 'sha256'), 'hex');
  select * into i from public.invite_codes where code_hash = v_hash for update;
  if i.id is null or i.revoked_at is not null or (i.expires_at is not null and i.expires_at < now())
     or (i.max_uses is not null and i.used_count >= i.max_uses) then
    raise exception 'Mã mời không hợp lệ hoặc đã hết hạn' using errcode = '22023';
  end if;
  if p_branch is not null and not exists (select 1 from public.clan_branches where id = p_branch and clan_id = i.clan_id) then
    raise exception 'Chi/nhánh không thuộc gia tộc' using errcode = '22023';
  end if;
  select * into m from public.clan_members where clan_id = i.clan_id and user_id = auth.uid();
  if m.id is not null and m.status in ('active', 'pending', 'locked') then
    raise exception 'Bạn đã gửi yêu cầu hoặc đã là thành viên' using errcode = '23505';
  end if;
  insert into public.clan_members(clan_id, user_id, status, role, branch_id, claimed_name, join_note,
                                  related_person_id, related_relation)
  values (i.clan_id, auth.uid(), 'pending', i.default_role, coalesce(p_branch, i.branch_id), p_claimed_name, p_note,
          p_related_person, p_related_relation)
  on conflict (clan_id, user_id) do update set status = 'pending', claimed_name = excluded.claimed_name,
     join_note = excluded.join_note, branch_id = excluded.branch_id, requested_at = now(),
     related_person_id = excluded.related_person_id, related_relation = excluded.related_relation
  returning * into m;
  update public.invite_codes set used_count = used_count + 1 where id = i.id;
  select name into v_name from public.clans where id = i.clan_id;
  -- thông báo cho người có quyền duyệt
  insert into public.notifications(user_id, clan_id, kind, title, body, payload)
  select cm.user_id, i.clan_id, 'join_request', 'Yêu cầu tham gia mới',
         coalesce(p_claimed_name, 'Một người') || ' xin tham gia ' || v_name, jsonb_build_object('member_id', m.id)
  from public.clan_members cm
  where cm.clan_id = i.clan_id and cm.status = 'active' and public.role_rank(cm.role) >= public.role_rank('admin');
  return query select m.id, v_name, m.status;
end $$;

-- Xét duyệt: approve | reject | needs_info | lock | unlock
create or replace function public.decide_member(p_member uuid, p_decision text, p_note text default null)
returns public.clan_members
language plpgsql security definer set search_path = public, extensions as $$
declare m public.clan_members; me public.clan_members; v_status member_status;
begin
  select * into m from public.clan_members where id = p_member for update;
  if m.id is null or not public.can_manage_members(m.clan_id, m.branch_id) then
    raise exception 'Không có quyền' using errcode = '42501';
  end if;
  me := public.my_membership(m.clan_id);
  if m.user_id = auth.uid() then raise exception 'Không thể tự xét duyệt' using errcode = '42501'; end if;
  if public.role_rank(m.role) >= public.role_rank(me.role) and m.status = 'active' then
    raise exception 'Không thể thao tác với người có quyền ngang hoặc cao hơn' using errcode = '42501';
  end if;
  v_status := case p_decision
    when 'approve' then 'active' when 'reject' then 'rejected' when 'needs_info' then 'needs_info'
    when 'lock' then 'locked' when 'unlock' then 'active' else null end;
  if v_status is null then raise exception 'Quyết định không hợp lệ' using errcode = '22023'; end if;
  update public.clan_members set status = v_status, admin_note = p_note, decided_by = auth.uid(), decided_at = now()
  where id = p_member returning * into m;
  insert into public.notifications(user_id, clan_id, kind, title, body)
  values (m.user_id, m.clan_id, 'membership', 'Cập nhật yêu cầu tham gia',
          case v_status when 'active' then 'Yêu cầu của bạn đã được chấp thuận'
                        when 'rejected' then 'Yêu cầu của bạn bị từ chối'
                        when 'needs_info' then 'Quản trị viên cần bạn bổ sung thông tin'
                        when 'locked' then 'Tài khoản của bạn trong gia tộc đã bị khóa'
                        else 'Trạng thái thành viên đã thay đổi' end || coalesce(': ' || p_note, ''));
  return m;
end $$;

-- Gán quyền: chỉ người có hạng cao hơn cả quyền hiện tại lẫn quyền mới của đối tượng.
create or replace function public.set_member_role(p_member uuid, p_role clan_role, p_branch uuid default null)
returns public.clan_members
language plpgsql security definer set search_path = public, extensions as $$
declare m public.clan_members; my int;
begin
  select * into m from public.clan_members where id = p_member for update;
  if m.id is null then raise exception 'Không tìm thấy' using errcode = 'P0002'; end if;
  my := public.my_rank(m.clan_id);
  if my < public.role_rank('admin') or my <= public.role_rank(m.role) or my <= public.role_rank(p_role) then
    raise exception 'Không có quyền gán vai trò này' using errcode = '42501';
  end if;
  if p_role = 'branch_head' and p_branch is null then
    raise exception 'Trưởng chi phải gắn với một chi' using errcode = '22023';
  end if;
  update public.clan_members set role = p_role, branch_id = coalesce(p_branch, branch_id)
  where id = p_member returning * into m;
  if p_role = 'branch_head' then
    update public.clan_branches set head_user_id = m.user_id where id = p_branch and clan_id = m.clan_id;
  end if;
  return m;
end $$;

-- Chuyển quyền chủ gia tộc
create or replace function public.transfer_ownership(p_clan uuid, p_new_owner_member uuid) returns void
language plpgsql security definer set search_path = public, extensions as $$
declare m public.clan_members;
begin
  if public.my_rank(p_clan) < public.role_rank('owner') then
    raise exception 'Chỉ chủ gia tộc được chuyển quyền' using errcode = '42501';
  end if;
  select * into m from public.clan_members where id = p_new_owner_member and clan_id = p_clan and status = 'active';
  if m.id is null then raise exception 'Thành viên không hợp lệ' using errcode = '22023'; end if;
  update public.clan_members set role = 'patriarch' where clan_id = p_clan and user_id = auth.uid();
  update public.clan_members set role = 'owner' where id = m.id;
  update public.clans set owner_id = m.user_id where id = p_clan;
end $$;

-- Cập nhật hồ sơ có kiểm tra phiên bản: nếu bản ghi đã bị người khác sửa thì báo xung đột, không ghi đè.
create or replace function public.update_person_checked(p_id uuid, p_expected_version int, p_patch jsonb)
returns public.persons
language plpgsql security invoker set search_path = public, extensions as $$
declare cur public.persons;
begin
  select * into cur from public.persons where id = p_id for update;
  if cur.id is null then raise exception 'Không tìm thấy hoặc không có quyền' using errcode = 'P0002'; end if;
  if cur.version <> p_expected_version then
    raise exception 'CONFLICT:%', cur.version using errcode = '40001';
  end if;
  update public.persons set
    full_name = coalesce(p_patch->>'full_name', full_name),
    other_names = case when p_patch ? 'other_names' then p_patch->>'other_names' else other_names end,
    gender = coalesce((p_patch->>'gender')::gender, gender),
    birth_date = case when p_patch ? 'birth_date' then (p_patch->>'birth_date')::date else birth_date end,
    birth_order = case when p_patch ? 'birth_order' then (p_patch->>'birth_order')::int else birth_order end,
    is_deceased = coalesce((p_patch->>'is_deceased')::boolean, is_deceased),
    death_date = case when p_patch ? 'death_date' then (p_patch->>'death_date')::date else death_date end,
    death_lunar_day = case when p_patch ? 'death_lunar_day' then (p_patch->>'death_lunar_day')::int else death_lunar_day end,
    death_lunar_month = case when p_patch ? 'death_lunar_month' then (p_patch->>'death_lunar_month')::int else death_lunar_month end,
    death_lunar_year = case when p_patch ? 'death_lunar_year' then (p_patch->>'death_lunar_year')::int else death_lunar_year end,
    death_lunar_leap = coalesce((p_patch->>'death_lunar_leap')::boolean, death_lunar_leap),
    branch_id = case when p_patch ? 'branch_id' then (p_patch->>'branch_id')::uuid else branch_id end,
    biography = case when p_patch ? 'biography' then p_patch->>'biography' else biography end,
    privacy = coalesce(p_patch->>'privacy', privacy)
  where id = p_id returning * into cur;   -- RLS (security invoker) vẫn kiểm tra quyền sửa
  return cur;
end $$;

-- Rời gia tộc / xóa tài khoản: xóa dữ liệu cá nhân, giữ hồ sơ gia phả (thuộc gia tộc).
create or replace function public.leave_clan(p_clan uuid) returns void
language plpgsql security definer set search_path = public, extensions as $$
begin
  if public.my_rank(p_clan) = public.role_rank('owner') then
    raise exception 'Chủ gia tộc phải chuyển quyền trước khi rời' using errcode = '42501';
  end if;
  update public.clan_members set status = 'left' where clan_id = p_clan and user_id = auth.uid();
  update public.persons set linked_user_id = null where clan_id = p_clan and linked_user_id = auth.uid();
end $$;

-- Quyền thực thi: chỉ người đã đăng nhập
revoke all on function public.create_clan(text, text, text) from public, anon;
revoke all on function public.create_invite(uuid, uuid, int, int) from public, anon;
revoke all on function public.revoke_invite(uuid) from public, anon;
revoke all on function public.request_join(text, text, text, uuid, uuid, text) from public, anon;
revoke all on function public.decide_member(uuid, text, text) from public, anon;
revoke all on function public.set_member_role(uuid, clan_role, uuid) from public, anon;
revoke all on function public.transfer_ownership(uuid, uuid) from public, anon;
revoke all on function public.update_person_checked(uuid, int, jsonb) from public, anon;
revoke all on function public.leave_clan(uuid) from public, anon;
grant execute on function public.create_clan(text, text, text) to authenticated;
grant execute on function public.create_invite(uuid, uuid, int, int) to authenticated;
grant execute on function public.revoke_invite(uuid) to authenticated;
grant execute on function public.request_join(text, text, text, uuid, uuid, text) to authenticated;
grant execute on function public.decide_member(uuid, text, text) to authenticated;
grant execute on function public.set_member_role(uuid, clan_role, uuid) to authenticated;
grant execute on function public.transfer_ownership(uuid, uuid) to authenticated;
grant execute on function public.update_person_checked(uuid, int, jsonb) to authenticated;
grant execute on function public.leave_clan(uuid) to authenticated;

-- Hồ sơ tài khoản tự tạo khi đăng ký
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public, extensions as $$
begin
  insert into public.profiles(id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'display_name', split_part(coalesce(new.email, ''), '@', 1)))
  on conflict (id) do nothing;
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute function public.handle_new_user();

-- Realtime: phát thay đổi cho các bảng đồng bộ (RLS vẫn áp dụng cho từng người nhận)
do $$ begin
  alter publication supabase_realtime add table public.persons, public.parent_links, public.marriages,
    public.clan_events, public.clan_tasks, public.clan_members, public.notifications;
exception when others then null; end $$;

-- ---------------------------------------------------------------- Storage (ảnh gia tộc, ảnh hồ sơ, tài liệu)
-- Bucket riêng tư; đường dẫn quy ước: <clan_id>/<loại>/<tên file>
insert into storage.buckets (id, name, public, file_size_limit)
values ('clan-media', 'clan-media', false, 5242880)
on conflict (id) do nothing;

drop policy if exists clan_media_read on storage.objects;
create policy clan_media_read on storage.objects for select using (
  bucket_id = 'clan-media' and public.is_active_member(((storage.foldername(name))[1])::uuid));
drop policy if exists clan_media_write on storage.objects;
create policy clan_media_write on storage.objects for insert with check (
  bucket_id = 'clan-media' and public.can_edit_clan_data(((storage.foldername(name))[1])::uuid, null));
drop policy if exists clan_media_delete on storage.objects;
create policy clan_media_delete on storage.objects for delete using (
  bucket_id = 'clan-media' and public.can_edit_clan_data(((storage.foldername(name))[1])::uuid, null));
