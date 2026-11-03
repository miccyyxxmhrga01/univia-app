-- Run against an isolated database after all migrations. Fixtures roll back.
begin;
create function pg_temp.assert_univia(value boolean, message text) returns void
language plpgsql as $$ begin
    if value is not true then raise exception 'ASSERTION FAILED: %', message; end if;
end $$;

do $$
declare
    view_name text;
    role_name text;
begin
    foreach view_name in array array[
        'users', 'organizations', 'admin_applications', 'memberships', 'posts',
        'events', 'settings', 'notifications', 'organization_folders',
        'organization_documents', 'organization_renewals', 'renewal_requirements',
        'post_comments', 'post_likes', 'chat_messages', 'chat_rooms', 'notification_dismissals'
    ] loop
        perform pg_temp.assert_univia(exists (
            select 1 from pg_class c join pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public' and c.relname = view_name and c.relkind = 'v'
                and 'security_invoker=true' = any(c.reloptions)
        ), view_name || ' must exist and respect the caller permissions');
        perform pg_temp.assert_univia(has_table_privilege('service_role', 'public.' || view_name, 'SELECT'),
            'server can inspect ' || view_name);
        foreach role_name in array array['anon', 'authenticated'] loop
            perform pg_temp.assert_univia(not has_table_privilege(role_name, 'public.' || view_name, 'SELECT'),
                role_name || ' cannot bypass the API using ' || view_name);
        end loop;
    end loop;
end $$;

set local role service_role;
select public.bbco_commit(
    '{"uid":"univia-catalog-coordinator","email":"superadmin@gmail.com"}',
    jsonb_build_object('request_id', gen_random_uuid(), 'writes', '[
        {"op":"set","path":"posts/catalog-post/comments/catalog-comment","data":{"userId":"catalog-user","text":"A comment"}},
        {"op":"set","path":"posts/catalog-post/likes/catalog-user","data":{"userId":"catalog-user"}},
        {"op":"set","path":"chats/catalog-org/messages/catalog-message","data":{"senderId":"catalog-user","text":"Hello"}},
        {"op":"set","path":"chats/admins_all/messages/catalog-manager-message","data":{"senderId":"catalog-user","text":"Managers"}},
        {"op":"set","path":"notificationDismissals/catalog-user/items/catalog-notification","data":{"notificationId":"catalog-notification"}}
    ]'::jsonb)
);
select pg_temp.assert_univia((select post_id = 'catalog-post' and data->>'text' = 'A comment'
    from public.post_comments where id = 'catalog-comment'), 'comments retain parent and content');
select pg_temp.assert_univia((select post_id = 'catalog-post' from public.post_likes
    where path = 'posts/catalog-post/likes/catalog-user'), 'likes retain their parent');
select pg_temp.assert_univia((select chat_id = 'catalog-org' from public.chat_messages
    where id = 'catalog-message'), 'organization messages retain their chat');
select pg_temp.assert_univia((select chat_id = 'admins_all' from public.chat_messages
    where id = 'catalog-manager-message'), 'manager chat remains distinct');
select pg_temp.assert_univia((select message_count = 1 from public.chat_rooms
    where id = 'catalog-org'), 'room summary counts only its messages');
select pg_temp.assert_univia((select user_id = 'catalog-user' from public.notification_dismissals
    where id = 'catalog-notification'), 'dismissals retain their owner');
reset role;
rollback;
\echo 'Univia module catalog, nested relationships and API isolation tests passed.'
