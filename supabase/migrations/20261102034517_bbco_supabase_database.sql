-- Keep the current document shapes and IDs while moving persistence to Postgres.
-- Only the Firebase-authenticated Edge Function can execute the application RPCs.
create schema if not exists bbco_private;
revoke all on schema bbco_private from public, anon, authenticated;
grant usage on schema bbco_private to service_role;

create sequence bbco_private.document_version;

create table public.bbco_documents (
    path text primary key check (length(path) <= 2048 and path ~ '^[^/]+/[^/]+(/[^/]+/[^/]+)*$'),
    collection_path text generated always as (regexp_replace(path, '/[^/]+$', '')) stored,
    collection_id text generated always as (
        (string_to_array(path, '/'))[cardinality(string_to_array(path, '/')) - 1]
    ) stored,
    id text generated always as ((string_to_array(path, '/'))[cardinality(string_to_array(path, '/'))]) stored,
    data jsonb not null check (jsonb_typeof(data) = 'object'),
    version bigint not null default nextval('bbco_private.document_version'),
    updated_at timestamptz not null default now()
);
alter table public.bbco_documents enable row level security;
revoke all on public.bbco_documents from public, anon, authenticated;
grant select, insert, update, delete on public.bbco_documents to service_role;
grant usage on sequence bbco_private.document_version to service_role;

create index bbco_collection_path_idx on public.bbco_documents (collection_path, path);
create index bbco_collection_group_idx on public.bbco_documents (collection_id, path);
create index bbco_document_data_idx on public.bbco_documents using gin (data jsonb_path_ops);
create index bbco_org_idx on public.bbco_documents (collection_path, (data->>'organizationId'));
create index bbco_student_idx on public.bbco_documents (collection_path, (data->>'studentId'));
create unique index bbco_username_unique on public.bbco_documents (lower(data->>'username'))
    where collection_path = 'users' and coalesce(data->>'username', '') <> '';
create unique index bbco_user_email_unique on public.bbco_documents (lower(data->>'email'))
    where collection_path = 'users' and coalesce(data->>'email', '') <> '';
create unique index bbco_membership_unique on public.bbco_documents ((data->>'studentId'), (data->>'organizationId'))
    where collection_path = 'memberships';

create table bbco_private.coordinators (
    email text primary key check (email = lower(email)),
    firebase_uid text unique
);
alter table bbco_private.coordinators enable row level security;
-- This is the existing application's coordinator login, not a new account/password.
insert into bbco_private.coordinators (email) values ('superadmin@gmail.com');
grant select, update on bbco_private.coordinators to service_role;

create table bbco_private.write_receipts (
    actor_uid text not null,
    request_id uuid not null,
    created_at timestamptz not null default now(),
    primary key (actor_uid, request_id)
);
alter table bbco_private.write_receipts enable row level security;
create index bbco_receipt_age_idx on bbco_private.write_receipts (created_at);
grant select, insert on bbco_private.write_receipts to service_role;

create function bbco_private.document(p_path text) returns jsonb
language sql stable set search_path = '' as $$
    select data from public.bbco_documents where path = p_path;
$$;

create function bbco_private.is_coordinator(p_actor jsonb) returns boolean
language sql stable set search_path = '' as $$
    select coalesce(p_actor->>'uid', '') <> '' and exists (
        select 1 from bbco_private.coordinators c
        where c.email = lower(p_actor->>'email')
          and (c.firebase_uid is null or c.firebase_uid = p_actor->>'uid')
    );
$$;

create function bbco_private.is_admin(p_actor jsonb) returns boolean
language sql stable set search_path = '' as $$
    select coalesce(
        bbco_private.document('users/' || (p_actor->>'uid')) @> '{"role":"admin","status":"approved"}', false
    );
$$;

create function bbco_private.manages(p_actor jsonb, p_org text) returns boolean
language sql stable set search_path = '' as $$
    select coalesce(p_org, '') <> '' and bbco_private.is_admin(p_actor)
        and coalesce(bbco_private.document('organizations/' || p_org)->>'adminUid' = p_actor->>'uid', false);
$$;

create function bbco_private.is_member(p_actor jsonb, p_org text) returns boolean
language sql stable set search_path = '' as $$
    select exists (
        select 1 from public.bbco_documents
        where collection_path = 'memberships'
          and data @> jsonb_build_object('organizationId', p_org, 'studentId', p_actor->>'uid', 'status', 'approved')
    );
$$;

create function bbco_private.valid_path(p_path text) returns boolean
language sql immutable set search_path = '' as $$
    select case
        when p_path ~ '^(users|organizations|adminApplications|memberships|posts|events|settings|notifications|organizationFolders|organizationDocuments|organizationRenewals|renewalRequirements)/[^/]+$' then true
        when p_path ~ '^posts/[^/]+/(comments|likes)/[^/]+$' then true
        when p_path ~ '^chats/[^/]+/messages/[^/]+$' then true
        when p_path ~ '^notificationDismissals/[^/]+/items/[^/]+$' then true
        else false end;
$$;

create function bbco_private.can_read(p_actor jsonb, p_path text, p_data jsonb) returns boolean
language plpgsql stable set search_path = '' as $$
declare
    parts text[] := string_to_array(p_path, '/');
    uid text := p_actor->>'uid';
    org text := p_data->>'organizationId';
begin
    if coalesce(uid, '') = '' or not bbco_private.valid_path(p_path) then return false; end if;
    if bbco_private.is_coordinator(p_actor) then return true; end if;
    case parts[1]
    when 'users' then
        return parts[2] = uid or (
            bbco_private.is_admin(p_actor) and (
                (p_data->>'role' = 'admin' and p_data->>'status' = 'approved') or exists (
                    select 1 from public.bbco_documents m where m.collection_path = 'memberships'
                    and m.data->>'studentId' = parts[2]
                    and bbco_private.manages(p_actor, m.data->>'organizationId')
                )
            )
        );
    when 'organizations', 'settings', 'events' then return true;
    when 'posts' then
        if cardinality(parts) = 4 then
            return bbco_private.can_read(p_actor, 'posts/' || parts[2], bbco_private.document('posts/' || parts[2]));
        end if;
        return p_data is not null and (coalesce(p_data->>'audience', 'everyone') <> 'admins' or bbco_private.is_admin(p_actor));
    when 'memberships' then
        return p_data->>'studentId' = uid or p_data->>'status' = 'approved' or bbco_private.manages(p_actor, org);
    when 'adminApplications' then return p_data->>'uid' = uid;
    when 'organizationFolders', 'organizationDocuments', 'organizationRenewals', 'renewalRequirements' then
        return bbco_private.manages(p_actor, org);
    when 'notifications' then
        return p_data->>'userId' = uid or (p_data->>'recipientRole' = 'admin' and bbco_private.manages(p_actor, org));
    when 'notificationDismissals' then return parts[2] = uid;
    when 'chats' then
        if parts[2] = 'admins_all' then return bbco_private.is_admin(p_actor); end if;
        return bbco_private.manages(p_actor, parts[2]) or bbco_private.is_member(p_actor, parts[2]);
    else return false;
    end case;
end;
$$;

create function bbco_private.visible_data(p_actor jsonb, p_path text, p_data jsonb) returns jsonb
language plpgsql stable set search_path = '' as $$
begin
    if split_part(p_path, '/', 1) = 'memberships'
        and p_data->>'studentId' <> p_actor->>'uid'
        and not bbco_private.is_coordinator(p_actor)
        and not bbco_private.manages(p_actor, p_data->>'organizationId') then
        return p_data - array['submittedDocuments', 'applicationNote', 'email', 'phone', 'contactNumber'];
    end if;
    return p_data;
end;
$$;

create function bbco_private.sort_value(p_value jsonb) returns jsonb
language sql immutable set search_path = '' as $$
    select case when p_value->>'__type' = 'timestamp' then
        to_jsonb((p_value->>'seconds')::numeric + (p_value->>'nanoseconds')::numeric / 1000000000)
    else p_value end;
$$;

create function public.bbco_read(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable set search_path = '' as $$
declare
    result jsonb;
    filters jsonb := coalesce(p_request->'filters', '{}'::jsonb);
    collection text := p_request->>'collection';
    doc_path text := p_request->>'path';
    order_field text := p_request->>'order_by';
    descending boolean := coalesce((p_request->>'descending')::boolean, false);
    page_size integer := least(greatest(coalesce((p_request->>'limit')::integer, 500), 1), 500);
    page_offset integer := greatest(coalesce((p_request->>'offset')::integer, 0), 0);
begin
    if jsonb_typeof(filters) <> 'object' or (select count(*) from jsonb_object_keys(filters)) > 20 then
        raise exception 'Invalid query filters.' using errcode = '22023';
    end if;
    if coalesce(p_actor->>'uid', '') = '' then
        -- Username sign-in and duplicate checks need only a single, exact identity match.
        -- Never expose application documents or permit anonymous collection browsing.
        if collection not in ('users', 'adminApplications') or collection is null or doc_path is not null
            or coalesce((p_request->>'group')::boolean, false)
            or (select count(*) from jsonb_object_keys(filters)) <> 1
            or not (filters ? 'username' or filters ? 'email')
            or length(coalesce(filters->>'username', filters->>'email', '')) not between 3 and 320 then
            raise exception 'Sign in to access the database.' using errcode = '42501';
        end if;
        select coalesce(jsonb_agg(jsonb_build_object('path', d.path, 'version', d.version,
            'data', jsonb_build_object('uid', d.data->'uid', 'username', d.data->'username',
                'email', d.data->'email', 'role', d.data->'role', 'status', d.data->'status',
                'applicationId', d.data->'applicationId'))), '[]'::jsonb)
        into result from (
            select path, data, version from public.bbco_documents
            where collection_path = collection and data @> filters
            order by path limit 1 offset page_offset
        ) d;
        return jsonb_build_object('documents', result);
    end if;
    if doc_path is not null then
        if not bbco_private.valid_path(doc_path) then
            raise exception 'Invalid document path.' using errcode = '22023';
        end if;
        if exists (select 1 from public.bbco_documents d where d.path = doc_path
            and bbco_private.can_read(p_actor, d.path, d.data) is not true) then
            raise exception 'You do not have access to this document.' using errcode = '42501';
        end if;
    elsif collection is null or length(collection) > 2048 then
        raise exception 'A collection is required.' using errcode = '22023';
    end if;
    select coalesce(jsonb_agg(jsonb_build_object('path', d.path, 'data',
        bbco_private.visible_data(p_actor, d.path, d.data), 'version', d.version)), '[]'::jsonb)
    into result from (
        select d.path, d.data, d.version from public.bbco_documents d
        where ((doc_path is not null and d.path = doc_path) or
            (doc_path is null and case when coalesce((p_request->>'group')::boolean, false)
                then d.collection_id = collection else d.collection_path = collection end))
        and bbco_private.can_read(p_actor, d.path, d.data) is true
        and not exists (select 1 from jsonb_each(filters) f
            where (d.data #> string_to_array(f.key, '.')) is distinct from f.value)
        and (order_field is null or d.data #> string_to_array(order_field, '.') is not null)
        order by
            case when not descending then bbco_private.sort_value(d.data #> string_to_array(order_field, '.')) end asc nulls first,
            case when descending then bbco_private.sort_value(d.data #> string_to_array(order_field, '.')) end desc nulls last,
            d.path
        limit page_size offset page_offset
    ) d;
    return jsonb_build_object('documents', result);
end;
$$;

create function bbco_private.resolve_value(p_value jsonb, p_previous jsonb, p_time jsonb, p_merge boolean default false)
returns jsonb language plpgsql immutable set search_path = '' as $$
declare result jsonb; item record; resolved jsonb;
begin
    if jsonb_typeof(p_value) = 'object' then
        case p_value->>'__op'
        when 'timestamp' then return p_time;
        when 'increment' then
            if jsonb_typeof(p_value->'amount') <> 'number' then
                raise exception 'Invalid increment.' using errcode = '22023';
            end if;
            return to_jsonb((case when jsonb_typeof(p_previous) = 'number' then p_previous::numeric else 0 end)
                + (p_value->>'amount')::numeric);
        when 'delete' then return null;
        else
            result := case when p_merge and jsonb_typeof(p_previous) = 'object' then p_previous else '{}'::jsonb end;
            for item in select * from jsonb_each(p_value) loop
                resolved := bbco_private.resolve_value(item.value, p_previous->item.key, p_time, p_merge);
                if resolved is null then result := result - item.key;
                else result := jsonb_set(result, array[item.key], resolved, true); end if;
            end loop;
            return result;
        end case;
    elsif jsonb_typeof(p_value) = 'array' then
        select coalesce(jsonb_agg(bbco_private.resolve_value(v, null, p_time)), '[]'::jsonb)
        into result from jsonb_array_elements(p_value) v;
        return result;
    end if;
    return p_value;
end;
$$;

create function bbco_private.set_field(p_data jsonb, p_keys text[], p_value jsonb) returns jsonb
language plpgsql immutable set search_path = '' as $$
declare nested jsonb;
begin
    if cardinality(p_keys) = 1 then
        if p_value is null then return p_data - p_keys[1]; end if;
        return jsonb_set(p_data, p_keys, p_value, true);
    end if;
    nested := case when jsonb_typeof(p_data->p_keys[1]) = 'object' then p_data->p_keys[1] else '{}'::jsonb end;
    return jsonb_set(p_data, array[p_keys[1]], bbco_private.set_field(nested, p_keys[2:], p_value), true);
end;
$$;

create function bbco_private.can_write(p_actor jsonb, p_path text, p_old jsonb, p_new jsonb, p_writes jsonb)
returns boolean language plpgsql stable set search_path = '' as $$
declare
    parts text[] := string_to_array(p_path, '/');
    uid text := p_actor->>'uid';
    value jsonb := coalesce(p_new, p_old);
    org text := value->>'organizationId';
    parent_data jsonb;
begin
    if coalesce(uid, '') = '' or not bbco_private.valid_path(p_path) then return false; end if;
    if bbco_private.is_coordinator(p_actor) then return true; end if;
    case parts[1]
    when 'users' then
        if parts[2] <> uid or p_new is null then return false; end if;
        if p_old is null then
            return p_new->>'uid' = uid and lower(p_new->>'email') = lower(p_actor->>'email')
                and p_new->>'role' in ('student', 'admin') and coalesce(p_new->>'organizationId', '') = ''
                and coalesce(p_new->>'status', 'pending') = 'pending';
        end if;
        return (p_new - array['status', 'rejectionReason']) @> jsonb_build_object('uid', uid)
            and (p_new->'role') is not distinct from (p_old->'role')
            and (p_new->'organizationId') is not distinct from (p_old->'organizationId')
            and (p_new->'applicationId') is not distinct from (p_old->'applicationId')
            and lower(p_new->>'email') = lower(p_actor->>'email')
            and ((p_new->'status') is not distinct from (p_old->'status') or
                (p_old->>'role' = 'admin' and p_old->>'status' = 'rejected' and p_new->>'status' = 'pending'));
    when 'adminApplications' then
        return p_new is not null and p_new->>'uid' = uid and p_new->>'role' = 'admin'
            and lower(p_new->>'email') = lower(p_actor->>'email') and p_new->>'status' = 'pending'
            and coalesce(p_new->>'organizationId', '') = ''
            and (p_old is null or (p_old->>'uid' = uid and p_old->>'status' in ('pending', 'rejected')));
    when 'organizations' then
        return p_old is not null and p_new is not null and bbco_private.manages(p_actor, parts[2])
            and (p_new->'adminUid') is not distinct from (p_old->'adminUid')
            and (p_new->'organizationId') is not distinct from (p_old->'organizationId')
            and (p_new->'applicationId') is not distinct from (p_old->'applicationId')
            and (p_new->'category') is not distinct from (p_old->'category')
            and (p_new->'status') is not distinct from (p_old->'status')
            and (p_new->'renewalStatus') is not distinct from (p_old->'renewalStatus')
            and (p_new->'currentAcademicYear') is not distinct from (p_old->'currentAcademicYear');
    when 'memberships' then
        if bbco_private.manages(p_actor, org) then
            return p_old is null or ((p_new is null or p_new->>'studentId' = p_old->>'studentId')
                and (p_new is null or p_new->>'organizationId' = p_old->>'organizationId'));
        end if;
        if p_new is null then return p_old->>'studentId' = uid and p_old->>'status' = 'pending'; end if;
        return p_new->>'studentId' = uid and p_new->>'status' = 'pending'
            and parts[2] = uid || '_' || org
            and bbco_private.document('users/' || uid)->>'role' = 'student'
            and bbco_private.document('organizations/' || org) is not null
            and coalesce((bbco_private.document('organizations/' || org)->>'recruitmentOpen')::boolean, true)
            and (coalesce((bbco_private.document('organizations/' || org)->>'recruitmentLimit')::numeric, 0) <= 0 or
                (select count(*) from public.bbco_documents m where m.collection_path = 'memberships'
                    and m.data->>'organizationId' = org and m.data->>'status' = 'approved')
                < (bbco_private.document('organizations/' || org)->>'recruitmentLimit')::numeric)
            and (p_old is null or (p_old->>'studentId' = uid and p_old->>'status' in ('rejected', 'removed')
                and p_new->>'organizationId' = p_old->>'organizationId'))
            and (select count(*) from public.bbco_documents m where m.collection_path = 'memberships'
                and m.path <> p_path and m.data->>'studentId' = uid and m.data->>'status' in ('pending', 'approved')) < 3;
    when 'posts' then
        if cardinality(parts) = 4 then
            parent_data := bbco_private.document('posts/' || parts[2]);
            if bbco_private.can_read(p_actor, 'posts/' || parts[2], parent_data) is not true then return false; end if;
            if parts[3] = 'likes' then
                return (parts[4] = uid and (p_new is null or p_new->>'userId' = uid))
                    or (p_new is null and parent_data->>'createdBy' = uid);
            end if;
            return value->>'userId' = uid and (p_old is null or p_new is null or p_new->>'userId' = p_old->>'userId')
                or (p_new is null and parent_data->>'createdBy' = uid);
        end if;
        if p_old is null then
            return p_new->>'createdBy' = uid
                and (coalesce(org, '') = '' or bbco_private.manages(p_actor, org))
                and (coalesce(p_new->>'audience', 'everyone') <> 'admins' or bbco_private.is_admin(p_actor));
        end if;
        if p_old->>'createdBy' = uid then
            return p_new is null or ((p_new->'createdBy') is not distinct from (p_old->'createdBy')
                and (p_new->'organizationId') is not distinct from (p_old->'organizationId')
                and (p_new->'posterRole') is not distinct from (p_old->'posterRole'));
        end if;
        return p_new is not null and bbco_private.can_read(p_actor, p_path, p_old)
            and (p_new - array['likeCount', 'commentCount']) = (p_old - array['likeCount', 'commentCount'])
            and exists (select 1 from jsonb_array_elements(p_writes) w
                where w->>'path' like p_path || '/likes/%' or w->>'path' like p_path || '/comments/%');
    when 'chats' then
        return bbco_private.can_read(p_actor, p_path, value) and value->>'senderId' = uid
            and (p_old is null or p_new is null or p_new->>'senderId' = p_old->>'senderId');
    when 'notificationDismissals' then return parts[2] = uid;
    when 'notifications' then
        if p_old is not null then
            return bbco_private.can_read(p_actor, p_path, p_old)
                and (p_new is null or (p_new - 'read') = (p_old - 'read'));
        end if;
        return p_new is not null and (
            (p_new->>'recipientRole' = 'admin' and coalesce(p_new->>'userId', '') = ''
                and bbco_private.document('organizations/' || org) is not null
                and (coalesce(p_new->>'actorId', uid) = uid)
                and (coalesce(p_new->>'studentId', uid) = uid))
            or (bbco_private.manages(p_actor, org) and coalesce(p_new->>'userId', '') <> '')
        );
    when 'events' then
        return bbco_private.manages(p_actor, org)
            and (p_old is null or p_new is null or p_new->>'organizationId' = p_old->>'organizationId');
    when 'organizationFolders' then
        return bbco_private.manages(p_actor, org)
            and (p_old is null or p_new is null or p_new->>'organizationId' = p_old->>'organizationId');
    when 'organizationDocuments', 'organizationRenewals', 'renewalRequirements' then
        return bbco_private.manages(p_actor, org)
            and (p_old is null or p_new is null or p_new->>'organizationId' = p_old->>'organizationId')
            and (p_new is null or (
                (coalesce(p_new->>'status', 'pending') in ('pending', 'uploaded', 'submitted')
                    or (parts[1] = 'organizationDocuments' and p_new->>'status' = 'approved'))
                and coalesce(p_new->>'reviewedBy', '') = coalesce(p_old->>'reviewedBy', '')
                and nullif(p_new->'reviewedAt', 'null'::jsonb) is not distinct from nullif(p_old->'reviewedAt', 'null'::jsonb)
                and coalesce(p_new->>'approvedBy', '') = coalesce(p_old->>'approvedBy', '')
                and nullif(p_new->'approvedAt', 'null'::jsonb) is not distinct from nullif(p_old->'approvedAt', 'null'::jsonb)
            ));
    else return false;
    end case;
end;
$$;

create function public.bbco_commit(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql set search_path = '' as $$
declare
    writes jsonb := p_request->'writes';
    reads jsonb := coalesce(p_request->'reads', '[]'::jsonb);
    uid text := p_actor->>'uid';
    receipt_id uuid := (p_request->>'request_id')::uuid;
    operation jsonb; item record; old_data jsonb; new_data jsonb;
    doc_path text; lock_path text; parent_path text; resolved jsonb;
    now_value jsonb := jsonb_build_object('__type', 'timestamp', 'seconds', floor(extract(epoch from now())),
        'nanoseconds', (extract(microseconds from now())::bigint % 1000000) * 1000);
    version_now bigint;
begin
    if coalesce(uid, '') = '' then raise exception 'Sign in to write data.' using errcode = '42501'; end if;
    if jsonb_typeof(writes) <> 'array' or jsonb_array_length(writes) > 500
        or jsonb_typeof(reads) <> 'array' or jsonb_array_length(reads) > 500 or receipt_id is null then
        raise exception 'Invalid write batch.' using errcode = '22023';
    end if;
    -- Sorted advisory locks also cover absent documents and parent counters.
    -- The per-user lock makes the membership limit safe against simultaneous requests.
    for lock_path in
        select distinct lock_key from (
            select 'actor:' || uid as lock_key
            union all select 'receipt:' || uid || ':' || receipt_id
            union all select w->>'path' from jsonb_array_elements(writes) w
            union all select r->>'path' from jsonb_array_elements(reads) r
            union all select 'posts/' || split_part(w->>'path', '/', 2) from jsonb_array_elements(writes) w
                where w->>'path' ~ '^posts/[^/]+/(likes|comments)/'
        ) locks order by lock_key
    loop
        perform pg_advisory_xact_lock(hashtextextended(lock_path, 0));
    end loop;
    if exists (select 1 from bbco_private.write_receipts r where r.actor_uid = uid and r.request_id = receipt_id) then
        return '{"ok":true}'::jsonb;
    end if;
    for operation in select * from jsonb_array_elements(reads) loop
        select coalesce((select version from public.bbco_documents where path = operation->>'path'), 0) into version_now;
        if version_now <> (operation->>'version')::bigint then
            raise exception 'The data changed. Retry the transaction.' using errcode = '40001';
        end if;
    end loop;
    -- Bind the coordinator allowlist to its first authenticated Firebase UID.
    update bbco_private.coordinators set firebase_uid = uid
    where email = lower(p_actor->>'email') and firebase_uid is null;
    for operation in select * from jsonb_array_elements(writes) loop
        doc_path := operation->>'path';
        if bbco_private.valid_path(doc_path) is not true then
            raise exception 'Invalid document path.' using errcode = '22023';
        end if;
        select data into old_data from public.bbco_documents where path = doc_path;
        if operation->>'op' = 'delete' then
            new_data := null;
        elsif operation->>'op' in ('set', 'update') and jsonb_typeof(operation->'data') = 'object' then
            if operation->>'op' = 'update' then
                if old_data is null then raise exception 'Document not found.' using errcode = 'P0002'; end if;
                new_data := old_data;
                for item in select * from jsonb_each(operation->'data') loop
                    resolved := bbco_private.resolve_value(item.value, old_data #> string_to_array(item.key, '.'), now_value);
                    new_data := bbco_private.set_field(new_data, string_to_array(item.key, '.'), resolved);
                end loop;
            else
                new_data := bbco_private.resolve_value(operation->'data', old_data, now_value,
                    coalesce((operation->>'merge')::boolean, false));
            end if;
        else raise exception 'Invalid write operation.' using errcode = '22023';
        end if;
        if bbco_private.can_write(p_actor, doc_path, old_data, new_data, writes) is not true then
            raise exception 'You do not have permission to change this record.' using errcode = '42501';
        end if;
        if new_data is null then delete from public.bbco_documents where path = doc_path;
        else
            insert into public.bbco_documents as d (path, data) values (doc_path, new_data)
            on conflict (path) do update set data = excluded.data,
                version = nextval('bbco_private.document_version'), updated_at = now();
        end if;
    end loop;
    -- Counters come from actual child rows, never from untrusted client increments.
    for parent_path in select distinct 'posts/' || split_part(w->>'path', '/', 2)
        from jsonb_array_elements(writes) w where w->>'path' ~ '^posts/[^/]+(/(likes|comments)/[^/]+)?$'
    loop
        update public.bbco_documents p set data = p.data || jsonb_build_object(
            'likeCount', (select count(*) from public.bbco_documents d where d.collection_path = parent_path || '/likes'),
            'commentCount', (select count(*) from public.bbco_documents d where d.collection_path = parent_path || '/comments')),
            version = nextval('bbco_private.document_version'), updated_at = now()
        where p.path = parent_path;
    end loop;
    insert into bbco_private.write_receipts (actor_uid, request_id) values (uid, receipt_id);
    return '{"ok":true}'::jsonb;
end;
$$;

-- RPCs are service-only. The Edge Function supplies its own verified actor;
-- passing a forged p_actor from a mobile REST request cannot invoke these functions.
revoke all on all functions in schema bbco_private from public, anon, authenticated;
grant execute on all functions in schema bbco_private to service_role;
revoke all on function public.bbco_read(jsonb, jsonb), public.bbco_commit(jsonb, jsonb) from public, anon, authenticated;
grant execute on function public.bbco_read(jsonb, jsonb), public.bbco_commit(jsonb, jsonb) to service_role;

-- Readable, RLS-respecting views for the Supabase dashboard and SQL reports.
do $$
declare mapping record;
begin
    for mapping in select * from (values
        ('users', 'users'), ('organizations', 'organizations'), ('admin_applications', 'adminApplications'),
        ('memberships', 'memberships'), ('posts', 'posts'), ('events', 'events'), ('settings', 'settings'),
        ('notifications', 'notifications'), ('organization_folders', 'organizationFolders'),
        ('organization_documents', 'organizationDocuments'), ('organization_renewals', 'organizationRenewals'),
        ('renewal_requirements', 'renewalRequirements')
    ) as mappings(view_name, collection) loop
        execute format('create view public.%I with (security_invoker = true) as select id, data, version, updated_at from public.bbco_documents where collection_path = %L', mapping.view_name, mapping.collection);
        execute format('revoke all on public.%I from public, anon, authenticated', mapping.view_name);
        execute format('grant select on public.%I to service_role', mapping.view_name);
    end loop;
end;
$$;

comment on table public.bbco_documents is 'BBCO application documents in Supabase Postgres. All client access goes through the authenticated bbco-data function.';
