-- Firebase owns verification status. Profile fields supplied by a client never
-- satisfy this check. Preserve the privately provisioned coordinator allowlist.
create function bbco_private.verified_email(p_actor jsonb) returns boolean
language sql immutable security invoker set search_path = '' as $$
    select coalesce(p_actor->'email_verified' = 'true'::jsonb, false);
$$;

create function bbco_private.bisu_email(p_email text) returns boolean
language sql immutable security invoker set search_path = '' as $$
    select coalesce(lower(p_email) ~ '^[^[:space:]@]+@bisu[.]edu[.]ph$', false);
$$;

alter function public.bbco_read(jsonb, jsonb) rename to bbco_read_core;
alter function public.bbco_commit(jsonb, jsonb) rename to bbco_commit_core;
alter function public.bbco_access(jsonb, jsonb) rename to bbco_access_core;

create function public.bbco_read(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
begin
    -- Public exact username lookup is still needed for sign-in. An unverified
    -- account may inspect only its own pending profile to resume registration.
    if coalesce(p_actor->>'uid', '') <> ''
        and not bbco_private.is_coordinator(p_actor)
        and not bbco_private.verified_email(p_actor)
        and coalesce(p_request->>'path', '') <> 'users/' || (p_actor->>'uid') then
        raise exception 'Verify your email before using the app.' using errcode = '42501';
    end if;
    return public.bbco_read_core(p_actor, p_request);
end;
$$;

create function public.bbco_commit(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql volatile security invoker set search_path = '' as $$
declare
    uid text := p_actor->>'uid';
    writes jsonb := p_request->'writes';
    operation jsonb;
    is_coordinator boolean := bbco_private.is_coordinator(p_actor);
begin
    if coalesce(uid, '') = '' then
        raise exception 'Sign in before changing data.' using errcode = '42501';
    end if;
    if jsonb_typeof(writes) is distinct from 'array' then
        raise exception 'Invalid registration request.' using errcode = '22023';
    end if;
    if not is_coordinator then
        for operation in select * from jsonb_array_elements(writes) loop
            if operation->>'path' = 'users/' || uid
                and bbco_private.document('users/' || uid) is null
                and not bbco_private.bisu_email(p_actor->>'email') then
                raise exception 'New accounts require an email ending in @bisu.edu.ph.' using errcode = '42501';
            end if;
        end loop;
        if not bbco_private.verified_email(p_actor) then
            if jsonb_array_length(writes) <> 1
                or writes->0->>'op' <> 'set'
                or writes->0->>'path' <> 'users/' || uid
                or bbco_private.document('users/' || uid) is not null
                or not bbco_private.bisu_email(p_actor->>'email') then
                raise exception 'Verify your email before using the app.' using errcode = '42501';
            end if;
        end if;
    end if;
    -- Existing ownership checks still enforce Student-only self-registration,
    -- the authenticated email, immutable roles, and organization assignments.
    return public.bbco_commit_core(p_actor, p_request);
end;
$$;

create function public.bbco_access(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare
    uid text := p_actor->>'uid';
    profile jsonb;
begin
    if coalesce(uid, '') = '' then
        raise exception 'Sign in to load account access.' using errcode = '42501';
    end if;
    if not bbco_private.is_coordinator(p_actor) and not bbco_private.verified_email(p_actor) then
        profile := bbco_private.document('users/' || uid);
        return jsonb_build_object('coordinator', false, 'emailVerified', false,
            'hasProfile', coalesce(profile->>'role' in ('student', 'admin'), false), 'organizations', '[]'::jsonb);
    end if;
    return public.bbco_access_core(p_actor, p_request)
        || jsonb_build_object('emailVerified', bbco_private.verified_email(p_actor));
end;
$$;

revoke all on function bbco_private.verified_email(jsonb), bbco_private.bisu_email(text)
    from public, anon, authenticated;
grant execute on function bbco_private.verified_email(jsonb), bbco_private.bisu_email(text) to service_role;
revoke all on function public.bbco_read(jsonb, jsonb), public.bbco_commit(jsonb, jsonb), public.bbco_access(jsonb, jsonb),
    public.bbco_read_core(jsonb, jsonb), public.bbco_commit_core(jsonb, jsonb), public.bbco_access_core(jsonb, jsonb)
    from public, anon, authenticated;
grant execute on function public.bbco_read(jsonb, jsonb), public.bbco_commit(jsonb, jsonb), public.bbco_access(jsonb, jsonb)
    to service_role;
