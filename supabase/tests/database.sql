-- Run against an isolated Postgres database after applying the migration.
-- Every fixture is rolled back; no production data is needed.
begin;

create function pg_temp.assert_true(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is not true then raise exception 'ASSERTION FAILED: %', message; end if;
end $$;

create function pg_temp.commit_as(actor jsonb, writes jsonb, reads jsonb default '[]') returns jsonb
language sql as $$
    select public.bbco_commit(actor, jsonb_build_object('request_id', gen_random_uuid(), 'writes', writes, 'reads', reads));
$$;
create function pg_temp.save(actor jsonb, path text, data jsonb, merge boolean default false) returns void
language plpgsql as $$ begin
    perform pg_temp.commit_as(actor, jsonb_build_array(jsonb_build_object('op', 'set', 'path', path, 'data', data, 'merge', merge)));
end $$;
create function pg_temp.expect_error(actor jsonb, writes jsonb, expected_state text, reads jsonb default '[]') returns void
language plpgsql as $$ begin
    begin
        perform pg_temp.commit_as(actor, writes, reads);
    exception when others then
        if sqlstate = expected_state then return; end if;
        raise;
    end;
    raise exception 'Expected SQLSTATE %, but the operation was accepted.', expected_state;
end $$;

set local role service_role;
do $$
declare
    coordinator jsonb := '{"uid":"test-coordinator","email":"superadmin@gmail.com"}';
    student jsonb := '{"uid":"test-student","email":"student@bisu.edu.ph","email_verified":true}';
    manager jsonb := '{"uid":"test-manager","email":"manager@bisu.edu.ph","email_verified":true}';
    outsider jsonb := '{"uid":"test-outsider","email":"outsider@bisu.edu.ph","email_verified":true}';
    records jsonb; snapshot jsonb; saved_version bigint; receipt uuid := gen_random_uuid();
begin
    perform pg_temp.save(student, 'users/test-student', '{"uid":"test-student","email":"student@bisu.edu.ph","role":"student","username":"student_test","fullName":"Student","createdAt":{"__op":"timestamp"}}');
    perform pg_temp.save(outsider, 'users/test-outsider', '{"uid":"test-outsider","email":"outsider@bisu.edu.ph","role":"student","username":"outsider_test"}');
    perform pg_temp.save(manager, 'users/test-manager', '{"uid":"test-manager","email":"manager@bisu.edu.ph","role":"student","username":"manager_test"}');
    perform pg_temp.save(manager, 'adminApplications/test-application', '{"uid":"test-manager","email":"manager@bisu.edu.ph","role":"admin","status":"pending","organizationId":"","username":"manager_test","documents":[{"url":"private.pdf"}]}');

    perform pg_temp.expect_error(student, '[{"op":"update","path":"users/test-student","data":{"role":"admin","status":"approved"}}]', '42501');
    perform pg_temp.expect_error(student, '[{"op":"set","path":"users/test-student","data":{"uid":"test-student","email":"student@bisu.edu.ph","role":"student","fullName":"Overwrite attempt"}}]', '40001', '[{"path":"users/test-student","version":0}]');
    perform pg_temp.expect_error(manager, '[{"op":"update","path":"users/test-manager","data":{"status":"approved","organizationId":"test-org"}}]', '42501');
    perform pg_temp.expect_error(student, '[{"op":"set","path":"settings/academicYear","data":{"academicYear":"forged"}}]', '42501');
    perform pg_temp.expect_error(outsider, '[{"op":"delete","path":"users/test-student"}]', '42501');

    perform pg_temp.commit_as(coordinator, '[
        {"op":"update","path":"adminApplications/test-application","data":{"status":"approved","organizationId":"test-org"}},
        {"op":"update","path":"users/test-manager","data":{"status":"approved","organizationId":"test-org"}},
        {"op":"set","path":"organizations/test-org","data":{"organizationId":"test-org","adminUid":"test-manager","orgName":"Test Organization","category":"normal","recruitmentOpen":true}},
        {"op":"set","path":"settings/academicYear","data":{"academicYear":"2026-2027","isActive":true}}
    ]');
    perform pg_temp.assert_true(bbco_private.is_admin(manager), 'coordinator approval activates the manager');
    perform pg_temp.assert_true(not bbco_private.is_coordinator(student), 'student cannot become a coordinator');
    perform pg_temp.assert_true(bbco_private.manages(manager, 'test-org'), 'manager access follows authoritative organization ownership');

    records := public.bbco_read(null, '{"collection":"users","filters":{"username":"manager_test"},"limit":1}');
    perform pg_temp.assert_true(records #>> '{documents,0,data,email}' = 'manager@bisu.edu.ph', 'username login resolves the email');
    perform pg_temp.assert_true(not (records #> '{documents,0,data}' ? 'fullName'), 'pre-auth lookup returns only the identity projection');
    begin
        perform public.bbco_read(null, '{"collection":"users"}');
        raise exception 'Anonymous collection browsing was accepted';
    exception when insufficient_privilege then null; end;
    begin
        perform public.bbco_read(outsider, '{"path":"users/test-student"}');
        raise exception 'Another student profile was exposed';
    exception when insufficient_privilege then null; end;

    perform pg_temp.save(student, 'memberships/test-student_test-org', '{"studentId":"test-student","organizationId":"test-org","status":"pending","fullName":"Student","submittedDocuments":[{"url":"private.pdf"}],"applicationNote":"private note"}');
    perform pg_temp.expect_error(student, '[{"op":"update","path":"memberships/test-student_test-org","data":{"status":"approved"}}]', '42501');
    perform pg_temp.commit_as(manager, '[{"op":"update","path":"memberships/test-student_test-org","data":{"status":"approved","approvedAt":{"__op":"timestamp"}}}]');
    records := public.bbco_read(outsider, '{"collection":"memberships","filters":{"status":"approved"}}');
    perform pg_temp.assert_true(jsonb_array_length(records->'documents') = 1, 'approved member counts remain visible');
    perform pg_temp.assert_true(not (records #> '{documents,0,data}' ? 'submittedDocuments'), 'member directories do not expose application attachments');
    records := public.bbco_read(manager, '{"collection":"memberships","filters":{"organizationId":"test-org"}}');
    perform pg_temp.assert_true(records #> '{documents,0,data}' ? 'submittedDocuments', 'the manager can review submitted documents');

    perform pg_temp.save(manager, 'organizationFolders/test-folder', '{"organizationId":"test-org","folderName":"Documents","metadata":{"first":1,"second":2}}');
    perform pg_temp.save(manager, 'organizationFolders/test-folder', '{"metadata":{"first":3}}', true);
    perform pg_temp.commit_as(manager, '[{"op":"update","path":"organizationFolders/test-folder","data":{"metadata.second":{"__op":"delete"},"metadata.third":4}}]');
    perform pg_temp.assert_true(bbco_private.document('organizationFolders/test-folder')->'metadata' = '{"first":3,"third":4}', 'merge, dotted updates and deletion preserve sibling fields');
    perform pg_temp.save(manager, 'renewalRequirements/test-requirement', '{"organizationId":"test-org","status":"uploaded","fileUrl":"https://example.test/file.pdf","uploadedAt":{"__op":"timestamp"}}');
    perform pg_temp.save(manager, 'organizationDocuments/test-upload', '{"organizationId":"test-org","status":"approved","fileUrl":"https://example.test/file.pdf","uploadedBy":"test-manager"}');
    perform pg_temp.save(manager, 'organizationRenewals/test-renewal', '{"organizationId":"test-org","status":"pending","reviewedBy":"","reviewedAt":null,"rejectionReason":""}');
    perform pg_temp.expect_error(manager, '[{"op":"update","path":"renewalRequirements/test-requirement","data":{"status":"approved"}}]', '42501');
    perform pg_temp.expect_error(outsider, '[{"op":"delete","path":"organizationFolders/test-folder"}]', '42501');

    perform pg_temp.save(manager, 'posts/test-post', '{"createdBy":"test-manager","posterRole":"admin","organizationId":"test-org","textContent":"Hello","createdAt":{"__op":"timestamp"}}');
    saved_version := (select version from public.bbco_documents where path = 'posts/test-post');
    perform pg_temp.commit_as(student, '[
        {"op":"set","path":"posts/test-post/likes/test-student","data":{"userId":"test-student","createdAt":{"__op":"timestamp"}}},
        {"op":"update","path":"posts/test-post","data":{"likeCount":5000}}
    ]', jsonb_build_array(jsonb_build_object('path', 'posts/test-post', 'version', saved_version)));
    perform pg_temp.assert_true(bbco_private.document('posts/test-post')->>'likeCount' = '1', 'the server derives the like count from actual likes');
    perform pg_temp.expect_error(student, '[{"op":"update","path":"posts/test-post","data":{"likeCount":22}}]', '40001',
        jsonb_build_array(jsonb_build_object('path', 'posts/test-post', 'version', saved_version)));
    perform pg_temp.expect_error(student, '[{"op":"set","path":"posts/test-post/likes/test-outsider","data":{"userId":"test-outsider"}}]', '42501');
    perform pg_temp.commit_as(student, '[
        {"op":"set","path":"posts/test-post/comments/test-comment","data":{"userId":"test-student","text":"A comment","createdAt":{"__op":"timestamp"}}},
        {"op":"update","path":"posts/test-post","data":{"commentCount":{"__op":"increment","amount":1}}}
    ]');
    perform pg_temp.assert_true(bbco_private.document('posts/test-post')->>'commentCount' = '1', 'comment batches update the counter atomically');
    perform pg_temp.commit_as(manager, '[{"op":"delete","path":"posts/test-post/comments/test-comment"}]');
    perform pg_temp.assert_true(bbco_private.document('posts/test-post')->>'commentCount' = '0', 'post authors can moderate comments');

    -- Repeated request IDs must not apply increments a second time.
    snapshot := jsonb_build_object('request_id', receipt, 'writes', '[{"op":"update","path":"organizationFolders/test-folder","data":{"count":{"__op":"increment","amount":1}}}]'::jsonb);
    perform public.bbco_commit(manager, snapshot);
    perform public.bbco_commit(manager, snapshot);
    perform pg_temp.assert_true(bbco_private.document('organizationFolders/test-folder')->>'count' = '1', 'write receipts make retries idempotent');

    perform pg_temp.save(student, 'chats/test-org/messages/test-message', '{"senderId":"test-student","text":"Hi","createdAt":{"__op":"timestamp"}}');
    records := public.bbco_read(manager, '{"collection":"messages","group":true,"order_by":"createdAt","descending":true}');
    perform pg_temp.assert_true(jsonb_array_length(records->'documents') = 1, 'collection-group queries support organization chat notifications');
    records := public.bbco_read(outsider, '{"collection":"chats/test-org/messages"}');
    perform pg_temp.assert_true(jsonb_array_length(records->'documents') = 0, 'outsiders cannot read private chats');

    perform pg_temp.save(manager, 'notifications/test-notification', '{"userId":"test-student","organizationId":"test-org","title":"Approved","read":false}');
    perform pg_temp.expect_error(outsider, '[{"op":"update","path":"notifications/test-notification","data":{"read":true}}]', '42501');
    perform pg_temp.commit_as(student, '[{"op":"update","path":"notifications/test-notification","data":{"read":true}}]');
    perform pg_temp.save(student, 'notificationDismissals/test-student/items/test-notification', '{"notificationId":"test-notification","dismissedAt":{"__op":"timestamp"}}');
    perform pg_temp.expect_error(outsider, '[{"op":"set","path":"notificationDismissals/test-student/items/forged","data":{}}]', '42501');

    perform pg_temp.save(coordinator, 'organizations/closed-org', '{"organizationId":"closed-org","recruitmentOpen":false}');
    perform pg_temp.expect_error(outsider, '[{"op":"set","path":"memberships/test-outsider_closed-org","data":{"studentId":"test-outsider","organizationId":"closed-org","status":"pending"}}]', '42501');
    perform pg_temp.save(coordinator, 'organizations/second-org', '{"organizationId":"second-org"}');
    perform pg_temp.save(coordinator, 'organizations/third-org', '{"organizationId":"third-org"}');
    perform pg_temp.save(coordinator, 'organizations/fourth-org', '{"organizationId":"fourth-org"}');
    perform pg_temp.save(student, 'memberships/test-student_second-org', '{"studentId":"test-student","organizationId":"second-org","status":"pending"}');
    perform pg_temp.save(student, 'memberships/test-student_third-org', '{"studentId":"test-student","organizationId":"third-org","status":"pending"}');
    perform pg_temp.expect_error(student, '[{"op":"set","path":"memberships/test-student_fourth-org","data":{"studentId":"test-student","organizationId":"fourth-org","status":"pending"}}]', '42501');

    -- Failed batches must roll back earlier writes in the same batch.
    perform pg_temp.expect_error(student, '[
        {"op":"update","path":"users/test-student","data":{"fullName":"Should roll back"}},
        {"op":"update","path":"users/test-manager","data":{"fullName":"Forged"}}
    ]', '42501');
    perform pg_temp.assert_true(bbco_private.document('users/test-student')->>'fullName' = 'Student', 'partial batch writes roll back');

    records := public.bbco_read(coordinator, '{"collection":"users","limit":2,"offset":0}');
    perform pg_temp.assert_true(jsonb_array_length(records->'documents') = 2, 'query pagination first page');
    records := public.bbco_read(coordinator, '{"collection":"users","limit":2,"offset":2}');
    perform pg_temp.assert_true(jsonb_array_length(records->'documents') = 1, 'query pagination last page');
    perform pg_temp.assert_true(bbco_private.document('users/test-student') #>> '{createdAt,__type}' = 'timestamp', 'timestamps retain seconds and nanoseconds');
end $$;

reset role;
select pg_temp.assert_true(not has_table_privilege('anon', 'public.bbco_documents', 'SELECT'), 'anonymous REST cannot read documents');
select pg_temp.assert_true(not has_table_privilege('authenticated', 'public.bbco_documents', 'UPDATE'), 'mobile clients cannot write documents directly');
select pg_temp.assert_true(not has_function_privilege('anon', 'public.bbco_commit(jsonb,jsonb)', 'EXECUTE'), 'anonymous clients cannot forge an RPC actor');
select pg_temp.assert_true(not has_function_privilege('authenticated', 'public.bbco_read(jsonb,jsonb)', 'EXECUTE'), 'authenticated clients cannot forge an RPC actor');
select pg_temp.assert_true((select relrowsecurity from pg_class where oid = 'public.bbco_documents'::regclass), 'document storage has RLS enabled');
rollback;
\echo 'Database authorization and document behavior tests passed.'
