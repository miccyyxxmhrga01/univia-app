-- Run after all migrations. Verification status comes from a server-verified JWT.
begin;
create function pg_temp.assert_email(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is not true then raise exception 'ASSERTION FAILED: %', message; end if;
end $$;
create function pg_temp.email_write(actor jsonb, path text, data jsonb, operation text default 'set') returns void
language sql as $$
    select public.bbco_commit(actor, jsonb_build_object('request_id', gen_random_uuid(),
        'writes', jsonb_build_array(jsonb_build_object('op', operation, 'path', path, 'data', data))));
$$;
create function pg_temp.deny_email_write(actor jsonb, path text, data jsonb, operation text default 'set') returns void
language plpgsql as $$ begin
    begin perform pg_temp.email_write(actor, path, data, operation);
    exception when insufficient_privilege then return; end;
    raise exception 'Unexpected permission to write %', path;
end $$;
set local role service_role;
do $$
declare
    coordinator jsonb := '{"uid":"email-coordinator","email":"superadmin@gmail.com"}';
    pending jsonb := '{"uid":"email-student","email":"bal_123@bisu.edu.ph","email_verified":false}';
    verified jsonb := pending || '{"email_verified":true}';
    personal jsonb := '{"uid":"email-personal","email":"student@gmail.com","email_verified":true}';
    access jsonb;
    actor jsonb;
    address text;
begin
    foreach address in array array['student@gmail.com', 'student@sub.bisu.edu.ph', 'student@bisu.edu.ph.attacker.com',
        'student@bisu.edu.ph@gmail.com', 'Student <student@bisu.edu.ph>', 'student @bisu.edu.ph', '@bisu.edu.ph'] loop
        perform pg_temp.assert_email(not bbco_private.bisu_email(address), 'fake BISU domain rejected: ' || address);
    end loop;
    perform pg_temp.assert_email(bbco_private.bisu_email('BAL_123@BISU.EDU.PH'), 'exact campus domain is case insensitive');
    perform pg_temp.deny_email_write(personal, 'users/email-personal', '{"uid":"email-personal","email":"student@gmail.com","role":"student"}');
    perform pg_temp.email_write(pending, 'users/email-student', '{"uid":"email-student","email":"bal_123@bisu.edu.ph","role":"student","emailVerified":true}');
    access := public.bbco_access(pending, '{"emailVerified":true}');
    perform pg_temp.assert_email((access->>'hasProfile')::boolean, 'unverified users can resume their pending profile');
    perform pg_temp.assert_email(not (access->>'emailVerified')::boolean, 'a client profile flag cannot verify email');
    perform pg_temp.assert_email(jsonb_array_length(access->'organizations') = 0, 'pending accounts have no manager access');
    perform pg_temp.assert_email(jsonb_array_length(public.bbco_read(pending, '{"path":"users/email-student"}')->'documents') = 1,
        'pending account can read its own profile');
    begin
        perform public.bbco_read(pending, '{"collection":"organizations"}');
        raise exception 'Pending account read application data';
    exception when insufficient_privilege then null; end;
    perform pg_temp.deny_email_write(pending, 'users/email-student', '{"fullName":"Unverified edit"}', 'update');
    perform pg_temp.deny_email_write(pending, 'posts/email-post', '{"createdBy":"email-student","textContent":"Unverified"}');
    perform pg_temp.deny_email_write(pending, 'adminApplications/email-application',
        '{"uid":"email-student","email":"bal_123@bisu.edu.ph","role":"admin","status":"pending"}');

    perform pg_temp.email_write(coordinator, 'organizations/email-org', '{"organizationId":"email-org","adminUid":"email-student"}');
    access := public.bbco_access(pending, '{}');
    perform pg_temp.assert_email(jsonb_array_length(access->'organizations') = 0, 'assignment cannot bypass email verification');
    perform pg_temp.deny_email_write(pending, 'events/email-event', '{"organizationId":"email-org"}');
    access := public.bbco_access(verified, '{}');
    perform pg_temp.assert_email((access->>'emailVerified')::boolean and jsonb_array_length(access->'organizations') = 1,
        'fresh verified token activates the same account and its assignments');
    perform pg_temp.email_write(verified, 'events/email-event', '{"organizationId":"email-org"}');
    perform pg_temp.email_write(verified, 'users/email-student', '{"fullName":"Verified Student"}', 'update');
    perform pg_temp.assert_email(bbco_private.document('users/email-student')->>'role' = 'student', 'verification keeps Student role');

    -- Missing or incorrectly typed claims fail closed even for an assigned manager.
    foreach actor in array array[pending - 'email_verified', pending || '{"email_verified":"true"}', pending || '{"email_verified":1}'] loop
        perform pg_temp.assert_email(not (public.bbco_access(actor, '{}')->>'emailVerified')::boolean, 'invalid verification claims are denied');
        perform pg_temp.deny_email_write(actor, 'events/email-event', '{"title":"Forged"}', 'update');
    end loop;
    access := public.bbco_access(coordinator, '{}');
    perform pg_temp.assert_email((access->>'coordinator')::boolean, 'private coordinator provisioning remains compatible');
end $$;
reset role;
select pg_temp.assert_email(not has_function_privilege('anon', 'public.bbco_commit_core(jsonb,jsonb)', 'EXECUTE'), 'anonymous clients cannot call core RPCs');
select pg_temp.assert_email(not has_function_privilege('authenticated', 'public.bbco_commit_core(jsonb,jsonb)', 'EXECUTE'), 'mobile clients cannot bypass verification');
rollback;
\echo 'BISU domain restrictions and email verification gates passed.'
