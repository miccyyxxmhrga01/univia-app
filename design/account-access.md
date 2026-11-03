# Coordinator-assigned access

Mode: Operate. Native Android. Existing visual system retained.

- New public accounts are Students. The shared sign-in derives access from the server.
- Coordinators are provisioned privately through the existing allowlist.
- Organizations.adminUid is the authoritative assignment. Assigning or removing it leaves profiles and credentials intact.
- Coordinators can find existing accounts by school ID or email, and confirm assignment, replacement and revocation. Stale dialogs must reload instead of replacing a newer assignment.
- Assigned users enter through Profile > Manage organization and can return to Student view. A picker appears only for already-assigned organizations.
- Organization applications retain five documents, rejection reasons and resubmission. Approval and access assignment are separate actions.
- Permission loss is enforced by the server for each read/write, and foreground UI checks run every three seconds. Unverified privileged controls are covered during loading or connection failures.
- Coordinator-assigned access and the email verification gate were deployed together with bbco-data v2 on 2026-11-03. Public accounts must verify their BISU email before entering a dashboard.
