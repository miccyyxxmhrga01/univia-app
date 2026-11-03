-- Regression checks for independent organization assignments. Fixtures roll back.
begin;

create function pg_temp.assert_multi_org(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is not true then raise exception 'ASSERTION FAILED: %', message; end if;
end $$;

create function pg_temp.save_multi_org(actor jsonb, path text, data jsonb, operation text default 'set') returns void
language sql as $$
    select public.bbco_commit(actor, jsonb_build_object('request_id', gen_random_uuid(),
        'writes', jsonb_build_array(jsonb_build_object('op', operation, 'path', path, 'data', data))));
$$;

set local role service_role;
do $$
declare
    coordinator jsonb := '{"uid":"multi-coordinator","email":"superadmin@gmail.com"}';
    manager jsonb := '{"uid":"multi-manager","email":"multi-manager@bisu.edu.ph","email_verified":true}';
    applicant jsonb := '{"uid":"multi-applicant","email":"multi-applicant@bisu.edu.ph","email_verified":true}';
    access jsonb;
    records jsonb;
    original_profile jsonb;
    original_version bigint;
begin
    perform pg_temp.save_multi_org(manager, 'users/multi-manager',
        '{"uid":"multi-manager","email":"multi-manager@bisu.edu.ph","role":"student","fullName":"Multiple Organizations","studentId":"multi-111"}');
    perform pg_temp.save_multi_org(applicant, 'users/multi-applicant',
        '{"uid":"multi-applicant","email":"multi-applicant@bisu.edu.ph","role":"student","studentId":"multi-222"}');
    original_profile := bbco_private.document('users/multi-manager');

    -- Reviewing an application alone must not change permissions or the account.
    perform pg_temp.save_multi_org(applicant, 'adminApplications/multi-application',
        '{"uid":"multi-applicant","email":"multi-applicant@bisu.edu.ph","role":"admin","status":"pending"}');
    perform pg_temp.save_multi_org(coordinator, 'adminApplications/multi-application', '{"status":"approved"}', 'update');
    access := public.bbco_access(applicant, '{}');
    perform pg_temp.assert_multi_org(jsonb_array_length(access->'organizations') = 0,
        'application approval does not grant management access');

    perform pg_temp.save_multi_org(coordinator, 'organizations/multi-first',
        '{"organizationId":"multi-first","orgName":"First","adminUid":"multi-manager"}');
    perform pg_temp.save_multi_org(coordinator, 'organizations/multi-second',
        '{"organizationId":"multi-second","orgName":"Second","adminUid":"multi-manager"}');
    perform pg_temp.save_multi_org(manager, 'organizationFolders/multi-first-private',
        '{"organizationId":"multi-first","name":"First private folder"}');
    perform pg_temp.save_multi_org(manager, 'organizationFolders/multi-second-private',
        '{"organizationId":"multi-second","name":"Second private folder"}');
    access := public.bbco_access(manager, '{}');
    perform pg_temp.assert_multi_org(jsonb_array_length(access->'organizations') = 2,
        'one Student can manage two independently assigned organizations');

    -- Removing one assignment must leave the other available and writable.
    select version into original_version from public.bbco_documents where path = 'organizations/multi-first';
    perform pg_temp.save_multi_org(coordinator, 'organizations/multi-first', '{"adminUid":""}', 'update');
    access := public.bbco_access(manager, '{}');
    perform pg_temp.assert_multi_org(jsonb_array_length(access->'organizations') = 1
        and access #>> '{organizations,0,id}' = 'multi-second', 'revocation only removes its organization');
    records := public.bbco_read(manager, '{"collection":"organizationFolders"}');
    perform pg_temp.assert_multi_org(jsonb_array_length(records->'documents') = 1
        and records #>> '{documents,0,data,organizationId}' = 'multi-second',
        'private collection queries exclude the revoked organization');
    begin
        perform public.bbco_read(manager, '{"path":"organizationFolders/multi-first-private"}');
        raise exception 'A revoked manager read a private document by its known path';
    exception when insufficient_privilege then null; end;
    perform pg_temp.save_multi_org(manager, 'organizationFolders/multi-second-private', '{"name":"Still editable"}', 'update');
    begin
        perform pg_temp.save_multi_org(manager, 'organizationFolders/multi-first-private', '{"name":"Stale editor"}', 'update');
        raise exception 'A revoked manager edited a private document';
    exception when insufficient_privilege then null; end;

    -- An older coordinator screen must not overwrite a newer assignment state.
    begin
        perform public.bbco_commit(coordinator, jsonb_build_object('request_id', gen_random_uuid(),
            'reads', jsonb_build_array(jsonb_build_object('path', 'organizations/multi-first', 'version', original_version)),
            'writes', '[{"op":"update","path":"organizations/multi-first","data":{"adminUid":"multi-applicant"}}]'::jsonb));
        raise exception 'A stale coordinator screen overwrote a newer assignment';
    exception when serialization_failure then null; end;
    perform pg_temp.assert_multi_org(bbco_private.document('organizations/multi-first')->>'adminUid' = '',
        'failed stale assignment leaves revocation intact');

    perform pg_temp.save_multi_org(coordinator, 'organizations/multi-second', '{"adminUid":""}', 'update');
    access := public.bbco_access(manager, '{}');
    perform pg_temp.assert_multi_org((access->>'hasProfile')::boolean
        and jsonb_array_length(access->'organizations') = 0 and not bbco_private.is_admin(manager),
        'last revocation returns the account to Student-only access');
    perform pg_temp.assert_multi_org(bbco_private.document('users/multi-manager') = original_profile,
        'assignments and revocations never mutate the Student profile');

    -- User-editable manager-looking fields cannot restore management permissions.
    perform pg_temp.save_multi_org(manager, 'users/multi-manager',
        '{"adminUid":"multi-manager","coordinator":true,"managedOrganizations":["multi-first"]}', 'update');
    access := public.bbco_access(manager, '{"organizations":[{"id":"multi-first"}],"coordinator":true}');
    perform pg_temp.assert_multi_org(not (access->>'coordinator')::boolean
        and jsonb_array_length(access->'organizations') = 0, 'client flags cannot restore removed access');
end $$;
reset role;
rollback;
\echo 'Multiple organization, private read revocation, stale assignment, and approval separation tests passed.'
