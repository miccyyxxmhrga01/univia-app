-- Run after all migrations. All test identities and assignments are rolled back.
begin;
create function pg_temp.assert_access(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is not true then raise exception 'ASSERTION FAILED: %', message; end if;
end $$;
create function pg_temp.write_access(actor jsonb, path text, data jsonb, operation text default 'set') returns void
language sql as $$
    select public.bbco_commit(actor, jsonb_build_object('request_id', gen_random_uuid(),
        'writes', jsonb_build_array(jsonb_build_object('op', operation, 'path', path, 'data', data))));
$$;
create function pg_temp.deny_access(actor jsonb, path text, data jsonb, operation text default 'set') returns void
language plpgsql as $$ begin
    begin perform pg_temp.write_access(actor, path, data, operation);
    exception when insufficient_privilege then return; end;
    raise exception 'Unexpected permission to write %', path;
end $$;
set local role service_role;
do $$
declare
    coordinator jsonb := '{"uid":"access-coordinator","email":"superadmin@gmail.com"}';
    alice jsonb := '{"uid":"access-alice","email":"alice@bisu.edu.ph","email_verified":true}';
    bob jsonb := '{"uid":"access-bob","email":"bob@bisu.edu.ph","email_verified":true}';
    legacy jsonb := '{"uid":"access-legacy","email":"legacy@bisu.edu.ph","email_verified":true}';
    newcomer jsonb := '{"uid":"access-new","email":"new@bisu.edu.ph","email_verified":true}';
    access jsonb;
begin
    perform pg_temp.deny_access(newcomer, 'users/access-new',
        '{"uid":"access-new","email":"new@bisu.edu.ph","role":"admin","status":"pending"}');
    perform pg_temp.write_access(alice, 'users/access-alice',
        '{"uid":"access-alice","email":"alice@bisu.edu.ph","role":"student","studentId":"111"}');
    perform pg_temp.write_access(bob, 'users/access-bob',
        '{"uid":"access-bob","email":"bob@bisu.edu.ph","role":"student","studentId":"222"}');
    -- Existing approved accounts are preserved; assignment is still authoritative.
    perform pg_temp.write_access(coordinator, 'users/access-legacy',
        '{"uid":"access-legacy","email":"legacy@bisu.edu.ph","role":"admin","status":"approved"}');
    perform pg_temp.write_access(coordinator, 'organizations/access-one',
        '{"organizationId":"access-one","orgName":"First Organization","adminUid":"access-legacy"}');
    perform pg_temp.write_access(coordinator, 'organizations/access-two',
        '{"organizationId":"access-two","orgName":"Second Organization","adminUid":""}');
    perform pg_temp.assert_access(bbco_private.manages(legacy, 'access-one'), 'legacy manager remains authorized');
    access := public.bbco_access(coordinator, '{}');
    perform pg_temp.assert_access((access->>'coordinator')::boolean, 'coordinator can log in without a Student profile');
    access := public.bbco_access(alice, '{"coordinator":true}');
    perform pg_temp.assert_access(not (access->>'coordinator')::boolean, 'caller flags cannot grant coordinator access');
    perform pg_temp.assert_access(jsonb_array_length(access->'organizations') = 0, 'new Students have no manager access');
    perform pg_temp.deny_access(alice, 'users/access-alice', '{"role":"admin","status":"approved"}', 'update');
    perform pg_temp.deny_access(alice, 'organizations/access-one', '{"adminUid":"access-alice"}', 'update');
    perform pg_temp.deny_access(coordinator, 'organizations/access-two', '{"adminUid":"nonexistent-user"}', 'update');

    -- Replace a manager without changing or overwriting either account.
    perform pg_temp.write_access(coordinator, 'organizations/access-one',
        '{"adminUid":"access-alice","managerAssignedBy":"access-coordinator","managerAssignedAt":{"__op":"timestamp"}}', 'update');
    perform pg_temp.assert_access(bbco_private.manages(alice, 'access-one'), 'assigned Student gains organization access');
    perform pg_temp.assert_access(not bbco_private.manages(alice, 'access-two'), 'assignment is scoped to its organization');
    perform pg_temp.assert_access(not bbco_private.is_admin(legacy), 'replaced legacy manager loses access despite stored admin role');
    perform pg_temp.assert_access(bbco_private.document('users/access-alice')->>'role' = 'student', 'assignment preserves Student role');
    perform pg_temp.write_access(alice, 'events/access-event', '{"organizationId":"access-one","title":"Allowed"}');
    perform pg_temp.deny_access(alice, 'events/access-forged', '{"organizationId":"access-two","title":"Forbidden"}');
    perform pg_temp.deny_access(alice, 'organizations/access-one', '{"managerAssignedBy":"access-alice"}', 'update');
    perform pg_temp.write_access(alice, 'posts/access-post',
        '{"organizationId":"access-one","createdBy":"access-alice","posterRole":"admin","textContent":"Org post"}');
    perform pg_temp.write_access(alice, 'memberships/access-alice_access-two',
        '{"studentId":"access-alice","organizationId":"access-two","status":"pending"}');
    perform pg_temp.assert_access(bbco_private.document('memberships/access-alice_access-two') is not null,
        'assigned manager keeps Student membership features');
    perform pg_temp.write_access(alice, 'adminApplications/access-application',
        '{"uid":"access-alice","email":"alice@bisu.edu.ph","role":"admin","status":"pending","organizationName":"Proposed"}');
    perform pg_temp.write_access(coordinator, 'adminApplications/access-application', '{"status":"approved"}', 'update');
    perform pg_temp.assert_access(bbco_private.document('users/access-alice')->>'role' = 'student', 'application review does not change account role');

    -- An already-open UI cannot keep writing after transfer or revocation.
    perform pg_temp.write_access(coordinator, 'organizations/access-one', '{"adminUid":"access-bob"}', 'update');
    perform pg_temp.assert_access(not bbco_private.manages(alice, 'access-one'), 'transferred access is removed from former manager');
    perform pg_temp.deny_access(alice, 'events/access-event', '{"title":"Stale screen"}', 'update');
    perform pg_temp.deny_access(alice, 'posts/access-post', '{"textContent":"Stale author"}', 'update');
    perform pg_temp.write_access(bob, 'events/access-event', '{"title":"New manager"}', 'update');
    perform pg_temp.write_access(coordinator, 'organizations/access-one', '{"adminUid":""}', 'update');
    perform pg_temp.assert_access(not bbco_private.is_admin(bob), 'revocation immediately removes privileged access');
    perform pg_temp.deny_access(bob, 'organizationFolders/access-folder', '{"organizationId":"access-one"}');
    access := public.bbco_access(bob, '{}');
    perform pg_temp.assert_access((access->>'hasProfile')::boolean and jsonb_array_length(access->'organizations') = 0,
        'revocation retains the Student account');
    perform pg_temp.assert_access(not has_function_privilege('anon', 'public.bbco_access(jsonb,jsonb)', 'EXECUTE'),
        'anonymous clients cannot forge an access actor');
    begin
        perform public.bbco_access(null, '{}');
        raise exception 'Anonymous access lookup succeeded';
    exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
\echo 'Account assignment, transfer, revocation, and Student retention tests passed.'
