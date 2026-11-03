-- Univia keeps the document paths used by the Android app. These reporting
-- views expose its nested records without changing persistence or permissions.
comment on table public.bbco_documents is
    'Univia application data: profiles, organizations, applications, memberships, posts, events, files, renewals, notifications and chat. Client access uses the Firebase-authenticated bbco-data function.';
comment on schema bbco_private is
    'Univia internal authorization, coordinator allowlist and idempotent write receipts. Not exposed to mobile database roles.';

create view public.post_comments with (security_invoker = true) as
select path, id, split_part(path, '/', 2) as post_id, data, version, updated_at
from public.bbco_documents
where path ~ '^posts/[^/]+/comments/[^/]+$';

create view public.post_likes with (security_invoker = true) as
select path, id, split_part(path, '/', 2) as post_id, data, version, updated_at
from public.bbco_documents
where path ~ '^posts/[^/]+/likes/[^/]+$';

create view public.chat_messages with (security_invoker = true) as
select path, id, split_part(path, '/', 2) as chat_id, data, version, updated_at
from public.bbco_documents
where path ~ '^chats/[^/]+/messages/[^/]+$';

create view public.chat_rooms with (security_invoker = true) as
select split_part(path, '/', 2) as id, count(*) as message_count,
    max(updated_at) as updated_at
from public.bbco_documents
where path ~ '^chats/[^/]+/messages/[^/]+$'
group by split_part(path, '/', 2);

create view public.notification_dismissals with (security_invoker = true) as
select path, id, split_part(path, '/', 2) as user_id, data, version, updated_at
from public.bbco_documents
where path ~ '^notificationDismissals/[^/]+/items/[^/]+$';

revoke all on public.post_comments, public.post_likes, public.chat_messages,
    public.chat_rooms, public.notification_dismissals from public, anon, authenticated;
grant select on public.post_comments, public.post_likes, public.chat_messages,
    public.chat_rooms, public.notification_dismissals to service_role;

comment on view public.post_comments is 'Univia post comments, with the parent post ID.';
comment on view public.post_likes is 'Univia post reactions, with the parent post ID.';
comment on view public.chat_messages is 'Univia organization and manager chat messages, with the parent chat ID.';
comment on view public.chat_rooms is 'Univia chat rooms derived from stored messages; empty organization rooms are represented by organizations.';
comment on view public.notification_dismissals is 'Univia per-user notification dismissal records.';
