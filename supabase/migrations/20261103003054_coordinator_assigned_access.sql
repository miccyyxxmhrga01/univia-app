-- Student is the only self-registered role. Coordinators control organization
-- assignments without replacing Student profiles or changing their credentials.
create index bbco_manager_uid_idx on public.bbco_documents ((data->>'adminUid'))
    where collection_path = 'organizations';

create or replace function bbco_private.is_admin(p_actor jsonb) returns boolean
language sql stable set search_path = '' as $$
    select coalesce(p_actor->>'uid', '') <> ''
        and coalesce(bbco_private.document('users/' || (p_actor->>'uid'))->>'role' in ('student', 'admin'), false)
        and exists (select 1 from public.bbco_documents o
            where o.collection_path = 'organizations' and o.data->>'adminUid' = p_actor->>'uid');
$$;

create function public.bbco_access(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare
    profile jsonb := bbco_private.document('users/' || (p_actor->>'uid'));
    organizations jsonb;
begin
    if coalesce(p_actor->>'uid', '') = '' then
        raise exception 'Sign in to load account access.' using errcode = '42501';
    end if;
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', split_part(o.path, '/', 2), 'name', coalesce(o.data->>'orgName', 'Organization'))
        order by o.data->>'orgName', o.path), '[]'::jsonb)
    into organizations from public.bbco_documents o
    where o.collection_path = 'organizations' and o.data->>'adminUid' = p_actor->>'uid'
        and bbco_private.manages(p_actor, split_part(o.path, '/', 2));
    return jsonb_build_object('coordinator', bbco_private.is_coordinator(p_actor),
        'hasProfile', coalesce(profile->>'role' in ('student', 'admin'), false),
        'organizations', organizations);
end;
$$;
revoke all on function public.bbco_access(jsonb, jsonb) from public, anon, authenticated;
grant execute on function public.bbco_access(jsonb, jsonb) to service_role;

create or replace function bbco_private.can_read(p_actor jsonb, p_path text, p_data jsonb) returns boolean
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
                exists (select 1 from public.bbco_documents o where o.collection_path = 'organizations'
                    and o.data->>'adminUid' = parts[2]) or exists (
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

create or replace function bbco_private.can_write(p_actor jsonb, p_path text, p_old jsonb, p_new jsonb, p_writes jsonb)
returns boolean language plpgsql stable set search_path = '' as $$
declare
    parts text[] := string_to_array(p_path, '/');
    uid text := p_actor->>'uid';
    value jsonb := coalesce(p_new, p_old);
    org text := value->>'organizationId';
    parent_data jsonb;
begin
    if coalesce(uid, '') = '' or not bbco_private.valid_path(p_path) then return false; end if;
    -- Every assignment must reference a registered account. Permission is derived
    -- from organizations.adminUid; no client-editable role flag grants access.
    if parts[1] = 'organizations' and p_new is not null
        and coalesce(p_new->>'adminUid', '') <> ''
        and (p_new->'adminUid') is distinct from (p_old->'adminUid') then
        if not bbco_private.is_coordinator(p_actor) then return false; end if;
        if coalesce(bbco_private.document('users/' || (p_new->>'adminUid'))->>'role', '')
            not in ('student', 'admin') then return false; end if;
    end if;
    if bbco_private.is_coordinator(p_actor) then return true; end if;
    case parts[1]
    when 'users' then
        if parts[2] <> uid or p_new is null then return false; end if;
        if p_old is null then
            return p_new->>'uid' = uid and lower(p_new->>'email') = lower(p_actor->>'email')
                and p_new->>'role' = 'student' and coalesce(p_new->>'organizationId', '') = ''
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
            and bbco_private.document('users/' || uid)->>'role' in ('student', 'admin')
            and lower(p_new->>'email') = lower(p_actor->>'email') and p_new->>'status' = 'pending'
            and coalesce(p_new->>'organizationId', '') = ''
            and (p_old is null or (p_old->>'uid' = uid and p_old->>'status' in ('pending', 'rejected')));
    when 'organizations' then
        return p_old is not null and p_new is not null and bbco_private.manages(p_actor, parts[2])
            and (p_new->'adminUid') is not distinct from (p_old->'adminUid')
            and (p_new->'managerAssignedBy') is not distinct from (p_old->'managerAssignedBy')
            and (p_new->'managerAssignedAt') is not distinct from (p_old->'managerAssignedAt')
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
            and bbco_private.document('users/' || uid)->>'role' in ('student', 'admin')
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
            if coalesce(p_old->>'organizationId', '') <> ''
                and not bbco_private.manages(p_actor, p_old->>'organizationId') then return false; end if;
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

