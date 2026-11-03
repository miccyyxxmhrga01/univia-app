-- Codes are HMAC digests made at the Edge. Only that service can reserve,
-- deliver or verify challenges. No plaintext code is stored in Postgres.
create table bbco_private.email_sender_config (
    singleton boolean primary key default true check (singleton),
    smtp_user text not null,
    vault_secret_id uuid not null references vault.secrets(id),
    updated_by text not null,
    updated_at timestamptz not null default now()
);
alter table bbco_private.email_sender_config enable row level security;
revoke all on bbco_private.email_sender_config from public, anon, authenticated;
grant select, insert, update on bbco_private.email_sender_config to service_role;
create policy otp_server_access on bbco_private.email_sender_config for all to service_role using (true) with check (true);

create table bbco_private.email_otp_accounts (
    uid text primary key,
    verified_email text,
    verified_at timestamptz,
    last_sent_at timestamptz,
    hour_started_at timestamptz not null default now(),
    hour_count integer not null default 0 check (hour_count >= 0),
    day_started_at timestamptz not null default now(),
    day_count integer not null default 0 check (day_count >= 0)
);
create table bbco_private.email_otp_challenges (
    email text primary key check (email = lower(email)),
    uid text not null,
    challenge_id uuid not null unique,
    code_digest text check (code_digest ~ '^[0-9a-f]{64}$'),
    state text not null check (state in ('pending', 'sent', 'failed', 'expired', 'locked', 'consumed')),
    expires_at timestamptz not null,
    attempts integer not null default 0 check (attempts between 0 and 5),
    last_sent_at timestamptz not null default now(),
    hour_started_at timestamptz not null default now(),
    hour_count integer not null default 0 check (hour_count >= 0),
    day_started_at timestamptz not null default now(),
    day_count integer not null default 0 check (day_count >= 0)
);
alter table bbco_private.email_otp_accounts enable row level security;
alter table bbco_private.email_otp_challenges enable row level security;
revoke all on bbco_private.email_otp_accounts, bbco_private.email_otp_challenges from public, anon, authenticated;
grant select, insert, update on bbco_private.email_otp_accounts, bbco_private.email_otp_challenges to service_role;
create policy otp_server_access on bbco_private.email_otp_accounts for all to service_role using (true) with check (true);
create policy otp_server_access on bbco_private.email_otp_challenges for all to service_role using (true) with check (true);

-- Keep previously verified Firebase accounts working. New code verification is
-- an additional server-owned proof tied to BOTH the Firebase UID and email.
create or replace function bbco_private.verified_email(p_actor jsonb) returns boolean
language sql stable security invoker set search_path = '' as $$
    select coalesce(p_actor->'email_verified' = 'true'::jsonb, false) or exists (
        select 1 from bbco_private.email_otp_accounts a
        where a.uid = p_actor->>'uid' and a.verified_email = lower(p_actor->>'email') and a.verified_at is not null
    );
$$;

create function bbco_private.otp_identity(p_actor jsonb) returns text
language plpgsql stable security invoker set search_path = '' as $$
declare profile jsonb;
begin
    if coalesce(p_actor->>'uid', '') = '' or length(coalesce(p_actor->>'email', '')) not between 3 and 320 then
        raise exception 'Sign in to verify your email.' using errcode = '42501';
    end if;
    profile := bbco_private.document('users/' || (p_actor->>'uid'));
    if coalesce(profile->>'role', '') not in ('student', 'admin') then
        raise exception 'Complete Student registration before requesting a code.' using errcode = '42501';
    end if;
    -- New public profiles already require the exact BISU domain. Existing
    -- privately provisioned legacy profiles may verify their existing address.
    return lower(p_actor->>'email');
end;
$$;

create function bbco_private.otp_wait(p_last timestamptz, p_hour timestamptz, p_hour_count integer,
    p_day timestamptz, p_day_count integer) returns integer
language plpgsql stable security invoker set search_path = '' as $$
declare remaining integer := greatest(0, ceil(extract(epoch from p_last + interval '60 seconds' - now()))::integer);
begin
    if p_hour_count >= 5 then remaining := greatest(remaining, ceil(extract(epoch from p_hour + interval '1 hour' - now()))::integer); end if;
    if p_day_count >= 20 then remaining := greatest(remaining, ceil(extract(epoch from p_day + interval '24 hours' - now()))::integer); end if;
    return greatest(0, remaining);
end;
$$;

create function public.bbco_otp_status(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare v_email text; account bbco_private.email_otp_accounts%rowtype; challenge bbco_private.email_otp_challenges%rowtype;
begin
    if bbco_private.is_coordinator(p_actor) or bbco_private.verified_email(p_actor) then
        return jsonb_build_object('ok', true, 'verified', true, 'retry_after', 0, 'active', false, 'configured', exists (select 1 from bbco_private.email_sender_config));
    end if;
    v_email := bbco_private.otp_identity(p_actor);
    select * into account from bbco_private.email_otp_accounts a where a.uid = p_actor->>'uid';
    select * into challenge from bbco_private.email_otp_challenges c where c.email = v_email;
    return jsonb_build_object('ok', true, 'verified', false,
        'configured', exists (select 1 from bbco_private.email_sender_config),
        'retry_after', greatest(bbco_private.otp_wait(account.last_sent_at, account.hour_started_at, account.hour_count, account.day_started_at, account.day_count),
            bbco_private.otp_wait(challenge.last_sent_at, challenge.hour_started_at, challenge.hour_count, challenge.day_started_at, challenge.day_count)),
        'active', coalesce(challenge.uid = p_actor->>'uid' and challenge.state = 'sent' and challenge.expires_at > now(), false));
end;
$$;

create function public.bbco_otp_reserve(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql volatile security invoker set search_path = '' as $$
declare
    v_email text; v_uid text := p_actor->>'uid';
    account bbco_private.email_otp_accounts%rowtype;
    challenge bbco_private.email_otp_challenges%rowtype;
    wait_seconds integer; nonce uuid; digest text := p_request->>'digest';
begin
    if bbco_private.is_coordinator(p_actor) or bbco_private.verified_email(p_actor) then return '{"ok":true,"verified":true}'::jsonb; end if;
    v_email := bbco_private.otp_identity(p_actor);
    if coalesce(digest, '') !~ '^[0-9a-f]{64}$' then raise exception 'Invalid code digest.' using errcode = '22023'; end if;
    nonce := (p_request->>'challenge_id')::uuid;
    if nonce is null then raise exception 'Missing challenge ID.' using errcode = '22023'; end if;
    -- Serialize both budgets, including deletion/recreation of a Firebase user
    -- with the same email. Every sender takes the email lock before the UID lock.
    perform pg_advisory_xact_lock(hashtextextended('otp-email:' || v_email, 0));
    perform pg_advisory_xact_lock(hashtextextended('otp-user:' || v_uid, 0));
    insert into bbco_private.email_otp_accounts (uid) values (v_uid) on conflict do nothing;
    select * into account from bbco_private.email_otp_accounts a where a.uid = v_uid for update;
    select * into challenge from bbco_private.email_otp_challenges c where c.email = v_email for update;
    wait_seconds := greatest(bbco_private.otp_wait(account.last_sent_at, account.hour_started_at, account.hour_count, account.day_started_at, account.day_count),
        bbco_private.otp_wait(challenge.last_sent_at, challenge.hour_started_at, challenge.hour_count, challenge.day_started_at, challenge.day_count));
    if wait_seconds > 0 then return jsonb_build_object('ok', false, 'code', 'otp_resend_limited', 'retry_after', wait_seconds); end if;
    update bbco_private.email_otp_accounts a set last_sent_at = now(),
        hour_started_at = case when account.hour_started_at + interval '1 hour' <= now() then now() else account.hour_started_at end,
        hour_count = case when account.hour_started_at + interval '1 hour' <= now() then 1 else account.hour_count + 1 end,
        day_started_at = case when account.day_started_at + interval '24 hours' <= now() then now() else account.day_started_at end,
        day_count = case when account.day_started_at + interval '24 hours' <= now() then 1 else account.day_count + 1 end
        where a.uid = v_uid;
    insert into bbco_private.email_otp_challenges as c (email, uid, challenge_id, code_digest, state, expires_at,
        hour_started_at, hour_count, day_started_at, day_count)
    values (v_email, v_uid, nonce, digest, 'pending', now() + interval '10 minutes',
        case when challenge.hour_started_at is null or challenge.hour_started_at + interval '1 hour' <= now() then now() else challenge.hour_started_at end,
        case when challenge.hour_started_at is null or challenge.hour_started_at + interval '1 hour' <= now() then 1 else challenge.hour_count + 1 end,
        case when challenge.day_started_at is null or challenge.day_started_at + interval '24 hours' <= now() then now() else challenge.day_started_at end,
        case when challenge.day_started_at is null or challenge.day_started_at + interval '24 hours' <= now() then 1 else challenge.day_count + 1 end)
    on conflict (email) do update set uid = excluded.uid, challenge_id = excluded.challenge_id, code_digest = excluded.code_digest,
        state = 'pending', expires_at = excluded.expires_at, attempts = 0, last_sent_at = now(),
        hour_started_at = excluded.hour_started_at, hour_count = excluded.hour_count,
        day_started_at = excluded.day_started_at, day_count = excluded.day_count;
    return jsonb_build_object('ok', true, 'verified', false, 'challenge_id', nonce, 'retry_after', 60, 'expires_in', 600);
end;
$$;

create function public.bbco_otp_delivery(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql volatile security invoker set search_path = '' as $$
declare v_email text := bbco_private.otp_identity(p_actor); changed integer;
begin
    update bbco_private.email_otp_challenges c set
        state = case when p_request->'delivered' = 'true'::jsonb then 'sent' else 'failed' end,
        code_digest = case when p_request->'delivered' = 'true'::jsonb then c.code_digest else null end
    where c.email = v_email and c.uid = p_actor->>'uid' and c.challenge_id = (p_request->>'challenge_id')::uuid and c.state = 'pending';
    get diagnostics changed = row_count;
    return jsonb_build_object('ok', changed = 1);
end;
$$;

create function public.bbco_otp_inspect(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare v_email text; challenge bbco_private.email_otp_challenges%rowtype;
begin
    if bbco_private.is_coordinator(p_actor) or bbco_private.verified_email(p_actor) then return '{"ok":true,"verified":true}'::jsonb; end if;
    v_email := bbco_private.otp_identity(p_actor);
    select * into challenge from bbco_private.email_otp_challenges c where c.email = v_email and c.uid = p_actor->>'uid';
    if not found then return '{"ok":false,"code":"otp_no_code"}'::jsonb; end if;
    -- The nonce is returned only to the service, never to the Android app.
    return jsonb_build_object('ok', true, 'challenge_id', challenge.challenge_id);
end;
$$;

create function public.bbco_otp_verify(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql volatile security invoker set search_path = '' as $$
declare v_email text; challenge bbco_private.email_otp_challenges%rowtype;
begin
    if bbco_private.is_coordinator(p_actor) or bbco_private.verified_email(p_actor) then return '{"ok":true,"verified":true}'::jsonb; end if;
    v_email := bbco_private.otp_identity(p_actor);
    perform pg_advisory_xact_lock(hashtextextended('otp-email:' || v_email, 0));
    select * into challenge from bbco_private.email_otp_challenges c where c.email = v_email and c.uid = p_actor->>'uid' for update;
    if not found then return '{"ok":false,"code":"otp_no_code"}'::jsonb; end if;
    if challenge.challenge_id is distinct from (p_request->>'challenge_id')::uuid then return '{"ok":false,"code":"otp_replaced"}'::jsonb; end if;
    if challenge.state = 'locked' then return '{"ok":false,"code":"otp_attempts_exhausted"}'::jsonb; end if;
    if challenge.expires_at <= now() then
        update bbco_private.email_otp_challenges c set state = 'expired', code_digest = null where c.email = challenge.email;
        return '{"ok":false,"code":"otp_expired"}'::jsonb;
    end if;
    if challenge.state <> 'sent' then return '{"ok":false,"code":"otp_no_code"}'::jsonb; end if;
    if (p_request->>'digest') is distinct from challenge.code_digest then
        -- Return an error value, rather than raising: failed guesses MUST commit.
        update bbco_private.email_otp_challenges c set attempts = challenge.attempts + 1,
            state = case when challenge.attempts + 1 >= 5 then 'locked' else 'sent' end,
            code_digest = case when challenge.attempts + 1 >= 5 then null else c.code_digest end
            where c.email = v_email;
        return jsonb_build_object('ok', false, 'code', case when challenge.attempts + 1 >= 5 then 'otp_attempts_exhausted' else 'otp_invalid_code' end,
            'attempts_remaining', 5 - challenge.attempts - 1);
    end if;
    update bbco_private.email_otp_accounts a set verified_email = v_email, verified_at = now() where a.uid = p_actor->>'uid';
    update bbco_private.email_otp_challenges c set state = 'consumed', code_digest = null where c.email = v_email;
    return '{"ok":true,"verified":true}'::jsonb;
end;
$$;

revoke all on function bbco_private.otp_identity(jsonb), bbco_private.otp_wait(timestamptz,timestamptz,integer,timestamptz,integer),
    public.bbco_otp_status(jsonb,jsonb), public.bbco_otp_reserve(jsonb,jsonb), public.bbco_otp_delivery(jsonb,jsonb),
    public.bbco_otp_inspect(jsonb,jsonb), public.bbco_otp_verify(jsonb,jsonb) from public, anon, authenticated;
grant execute on function bbco_private.otp_identity(jsonb), bbco_private.otp_wait(timestamptz,timestamptz,integer,timestamptz,integer),
    public.bbco_otp_status(jsonb,jsonb), public.bbco_otp_reserve(jsonb,jsonb), public.bbco_otp_delivery(jsonb,jsonb),
    public.bbco_otp_inspect(jsonb,jsonb), public.bbco_otp_verify(jsonb,jsonb) to service_role;

create function public.bbco_otp_sender_status(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare result jsonb;
begin
    if not bbco_private.is_coordinator(p_actor) then raise exception 'Coordinator access is required.' using errcode = '42501'; end if;
    select jsonb_build_object('configured', true, 'email', c.smtp_user) into result from bbco_private.email_sender_config c;
    return coalesce(result, '{"configured":false,"email":""}'::jsonb);
end;
$$;

create function public.bbco_otp_sender_save(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql volatile security invoker set search_path = '' as $$
declare
    sender text := lower(p_request->>'email');
    password text := p_request->>'password';
    secret_id uuid;
begin
    if not bbco_private.is_coordinator(p_actor) then raise exception 'Coordinator access is required.' using errcode = '42501'; end if;
    if coalesce(sender, '') !~ '^[^[:space:]@<>]+@[^[:space:]@<>]+[.][^[:space:]@<>]+$'
        or length(sender) > 320 or coalesce(password, '') !~ '^[A-Za-z0-9]{16}$' then
        raise exception 'Enter the sending email and its 16-character app password.' using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('otp-sender-config', 0));
    select c.vault_secret_id into secret_id from bbco_private.email_sender_config c for update;
    if secret_id is null then
        secret_id := vault.create_secret(password, 'univia_smtp_' || gen_random_uuid(), 'Univia email code sender');
    else
        perform vault.update_secret(secret_id, password);
    end if;
    insert into bbco_private.email_sender_config (smtp_user, vault_secret_id, updated_by)
    values (sender, secret_id, p_actor->>'uid')
    on conflict (singleton) do update set smtp_user = excluded.smtp_user, updated_by = excluded.updated_by, updated_at = now();
    return jsonb_build_object('configured', true, 'email', sender);
end;
$$;

-- Internal transport credentials. The Edge never forwards this response to a
-- client, and the function is uncallable with mobile database roles.
create function public.bbco_otp_sender_secret(p_actor jsonb, p_request jsonb) returns jsonb
language plpgsql stable security invoker set search_path = '' as $$
declare result jsonb;
begin
    if not bbco_private.is_coordinator(p_actor) then perform bbco_private.otp_identity(p_actor); end if;
    select jsonb_build_object('email', c.smtp_user, 'password', s.decrypted_secret) into result
    from bbco_private.email_sender_config c join vault.decrypted_secrets s on s.id = c.vault_secret_id;
    return coalesce(result, '{}'::jsonb);
end;
$$;
revoke all on function public.bbco_otp_sender_status(jsonb,jsonb), public.bbco_otp_sender_save(jsonb,jsonb),
    public.bbco_otp_sender_secret(jsonb,jsonb) from public, anon, authenticated;
grant execute on function public.bbco_otp_sender_status(jsonb,jsonb), public.bbco_otp_sender_save(jsonb,jsonb),
    public.bbco_otp_sender_secret(jsonb,jsonb) to service_role;
