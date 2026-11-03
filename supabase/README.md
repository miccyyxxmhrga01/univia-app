# Univia Supabase database

Univia uses project `cwruxuwcoxldpyseavbb` at
`https://cwruxuwcoxldpyseavbb.supabase.co`. Complete the existing project by
applying all checked-in migrations and deploying the matching API function.
Existing Supabase records are preserved. Historical Firestore records have not
been imported or deleted.

`project_id = "Univia"` names the local Supabase environment. The cloud project's
display name is managed separately in Project Settings > General. Supabase's
managed PostgreSQL database retains its standard `postgres` connection name.

Firebase Authentication still handles passwords and sign-in. Firebase AI and
Cloudinary uploads remain in place. The Firestore and unused Firebase Storage
dependencies have been removed.

## Data and access

`public.bbco_documents` stores the app's document paths, JSONB data, monotonically
increasing versions, and update times. Keeping the existing IDs, nested
collections, and record shapes preserves the screens' existing workflows.
The following **views** make the top-level collections accessible for SQL reports
and inspection in the Supabase dashboard:

| View | Application collection |
| --- | --- |
| `users` | `users` |
| `organizations` | `organizations` |
| `admin_applications` | `adminApplications` |
| `memberships` | `memberships` |
| `posts` | `posts` |
| `events` | `events` |
| `settings` | `settings` |
| `notifications` | `notifications` |
| `organization_folders` | `organizationFolders` |
| `organization_documents` | `organizationDocuments` |
| `organization_renewals` | `organizationRenewals` |
| `renewal_requirements` | `renewalRequirements` |
| `post_comments` | `posts/{postId}/comments` |
| `post_likes` | `posts/{postId}/likes` |
| `chat_messages` | `chats/{chatId}/messages` |
| `chat_rooms` | Rooms summarized from stored chat messages |
| `notification_dismissals` | `notificationDismissals/{userId}/items` |

Likes, comments, chat messages, and notification dismissals use their existing
nested paths in `bbco_documents`. Timestamps retain seconds and nanoseconds.

The Android data layer calls the `bbco-data` Edge Function using the publishable
key and the current user's Firebase ID token. The function verifies Google's
RS256 signature, token expiry, and the Firebase project `bbco-3c6ca` before
constructing the database actor. It uses a server-side Supabase key to call
`bbco_read`, `bbco_commit`, and `bbco_access`. That key is never included in the APK.

The RPCs enforce ownership and organization permissions in Postgres. Anonymous
and authenticated mobile database roles cannot call them directly or access the
underlying tables/views. RLS is enabled on all three storage/configuration tables.
The only public operation is a single exact username/email lookup needed by the
existing organization sign-in and registration screens; it returns a restricted
identity projection, never application attachments.

Atomic batches, optimistic transaction retries, server timestamps, increments,
merge writes, and field deletion are supported. Post/comment counts are derived
from the stored likes/comments. Duplicate registration cannot overwrite an
existing profile. Write receipts prevent the same request from being applied twice.

## First use

- The existing Firebase account `superadmin@gmail.com` remains the coordinator
  login. No password was created or changed. Its Firebase UID is bound to the
  private coordinator allowlist on its first successful database write.
- Set the academic year through the coordinator screen, then approve organization
  applications as usual.
- New users create Student profiles through registration. Signed-in users submit organization
  applications from their profile. Coordinators assign managers separately after approval. Existing Firebase users must use their **current
  Firebase password** when registering a profile in this fresh database.
- Previous organizations, profiles, memberships, posts, and documents are still
  in Firestore and do not appear in this new database automatically.

## Live updates and offline behavior

Snapshot listeners refresh every three seconds while the app is in the foreground
and emit only changed snapshots. They stop refreshing in the background and stop
delivering data when the signed-in user changes. This is HTTP polling, rather than
a Supabase Realtime subscription. The data layer has no persistent offline cache;
database reads and writes require a connection.

## Configuration and deployment

The project URL and **publishable** key are in `app/build.gradle.kts` and become
BuildConfig fields. Only public client configuration belongs there.
The Edge Function reads `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` from its
Supabase runtime. `FIREBASE_PROJECT_ID` optionally overrides `bbco-3c6ca`.

`verify_jwt = false` in `config.toml` is intentional: the function verifies Firebase
tokens itself, rather than expecting Supabase Auth tokens at the gateway.

Apply the checked-in migrations in order and deploy
`functions/bbco-data/{index.ts,handler.ts,deno.json}` together. There are no external
runtime package dependencies in the Edge Function. Database schema and API code
must be deployed together: the Android sign-in flow needs the `bbco_access` RPC
and the API's `access` action.

The five migrations create the document store, private authorization tables,
indexes, permissions, all three RPCs, and 17 module views. Keep the `bbco_*`
internal identifiers: they are the Android app's existing API contract.

To prepare SQL bundles and the function source for review or dashboard deployment:

```powershell
python supabase/scripts/prepare-deployment.py
```

The output is `build/univia-database/deployment/`. `Univia-full.sql` targets a
fresh database. `Univia-upgrade.sql` targets the existing configured project
with only its first two migrations applied. Each bundle runs atomically and
records its migration versions. Use the bundle matching the database's state;
do not run the full bundle over an existing installation.

Write receipts are kept in `bbco_private.write_receipts`. A database administrator
may periodically remove receipts older than the application's retry window
(for example, seven days); retain recent receipts so retries stay idempotent.

## Verification

- Android: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
- Edge authentication tests (Node 24+):
  `node --test supabase/functions/bbco-data/handler.test.ts`
- Edge type checking:
  `npx --yes deno@2.9.6 check supabase/functions/bbco-data/index.ts`
- Deployed, read-only API checks: `python supabase/tests/smoke.py`
- Postgres behavior/permission tests: apply the migrations to a fresh isolated
  Postgres database, then run `psql -v ON_ERROR_STOP=1 -f supabase/tests/database.sql`.
  A standalone Postgres test cluster needs the `anon`, `authenticated`, and
  `service_role` roles, with `service_role` set to `BYPASSRLS`, as in Supabase.
  The test fixtures are rolled back.

The build produces `app/build/outputs/apk/debug/app-debug.apk`.
No real user credentials were used for automated tests; signed-in device testing
with the campus's actual accounts remains a useful final acceptance check.
Run the SQL suites against an isolated test database. They roll back their
fixtures and must not replace real application data. Run the deployed API smoke
checks after deploying both the migrations and function. A successful local
verification does not mean the cloud deployment has been updated.


## Coordinator-assigned access

All public registration creates `role = student`. The existing organization
`adminUid` is the authoritative manager assignment. Assignment, replacement and
revocation leave the Student profile and credentials intact. Existing legacy
`admin` profiles remain compatible when they are assigned to an organization.
An old `admin` role alone does not grant management access.

The shared sign-in calls the authenticated `access` action. It returns coordinator
status from the private coordinator allowlist, profile availability, and the
organizations the caller actually manages. It accepts no caller-supplied roles.
The coordinator uses Organizations > Manage organization access to find an
existing account by school ID or email and assign it. The screen checks document
versions before saving so a stale dialog cannot silently replace a newer manager.

Students enter management through Profile > Manage organization. That entry and
privileged screens refresh permissions every three seconds in the foreground,
and privileged controls are blocked while access cannot be verified. Every server
write checks the current organization assignment, including writes from a screen
opened before revocation. Student view remains available from the manager header.

Organization applications use the signed-in account and retain all five required
documents, review, rejection reasons and resubmission. Approval creates the
organization with no manager assigned. It does not overwrite the applicant's
profile or grant manager access. Public organization-manager account signup is
removed. Coordinator accounts are provisioned privately.

Additional verification:

- `psql -v ON_ERROR_STOP=1 -f supabase/tests/account-access.sql` after all migrations.
- `psql -v ON_ERROR_STOP=1 -f supabase/tests/account-access-multi-org.sql` checks independent assignments, private reads after revocation, stale coordinator changes, and approval without assignment. Run against an isolated test database; all fixtures roll back.
- `psql -v ON_ERROR_STOP=1 -f supabase/tests/univia-catalog.sql` checks all 17 module views, nested parent IDs, chat summaries, and direct-access isolation. Run against an isolated test database; all fixtures roll back.
- `AccountAccessTest` checks access response handling and profile retention.
- `AccountAccessUiTest` renders shared sign-in, assignment and application layouts
  without signing in or changing any real accounts.


## BISU email verification

Public registration accepts only the exact `@bisu.edu.ph` domain. The existing
Android format `bal_<schoolID>@bisu.edu.ph` must still match the entered Student ID.
A pending Student profile can be saved before verification so registration is
recoverable. Firebase then sends an email verification link. The verification
screen supports opening an email app, resend with a 60-second cooldown, status
refresh, and returning to sign-in. Verification must reload the Firebase user and
force-refresh the ID token before the app opens a dashboard.

The Edge Function forwards `email_verified` exclusively from the Google-signed
Firebase token. Missing, string-valued and numeric flags all count as unverified.
Database wrappers allow an unverified user to create/read only their own pending
profile. They block all other app data and writes, and the access response returns
no organization assignments. A client profile flag cannot activate the account.
The private coordinator allowlist remains compatible; it is separate from public
account registration. Existing legacy profiles can verify their existing email;
new public profiles require the BISU domain.

Deploy all migrations, including `20261103003124_bisu_email_verification.sql`, and
the current `bbco-data` function together before distributing the updated APK.
`supabase/tests/email-verification.sql` tests pending profiles, fake domains,
forged flags, verification activation and blocked management access. Verification
emails are sent only by Firebase in response to registration or a resend action;
automated tests never send email to a real mailbox.


Deployment status (2026-11-03): coordinator-assigned access and BISU email
verification are live in project `cwruxuwcoxldpyseavbb`; `bbco-data` is version 2.
Read-only live probes confirmed domain rejection, fail-closed verification, and
service-only core RPC privileges. The security advisor returned no findings.
The `20261103005448_univia_database_catalog` migration is also live, completing
all 17 module views. All ten deployed API smoke checks passed, including the
private chat view check. The existing user record was preserved. Deployment
verification is retained in `build/univia-database/deployment-verification.md`;
the earlier authentication checks are retained in `build/email-verification-review`.
