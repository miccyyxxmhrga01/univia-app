# Univia App

<!-- impeccable:product-schema 1 -->

## Platform

android

## Product Purpose

Univia App supports BISU Balilihan campus organization discovery, membership applications, organization administration, document review, and academic year renewal.

## Users

Students discover and join organizations. Org Managers manage an organization and its members. Coordinators oversee applications, documents, and renewals. These are presentation names; existing authentication and database role identifiers remain compatible.

## Capabilities and Constraints

Existing native Kotlin/XML application with Firebase Authentication, Supabase Postgres through the Firebase-authenticated bbco-data API, Cloudinary uploads, organization chat, events, announcements, and interest based organization recommendations. Preserve authentication, membership, document and approval workflows. The launch role chooser is intentionally replaced by an animated introduction and onboarding.

## Brand Commitments

The user requested a combination of neumorphism, glassmorphism and minimalism, with the login layout copied closely from their supplied image (`branding/login-design-reference.png`). The current palette keeps violet/orange accents and lavender glass surfaces; the latest request changes primary action buttons to a blue gradient. Use the supplied campus photo, centered BISU/Univia App logos and frosted cards. Student registration has five steps: Profile, Account, Interests, Review and Verify email. Email verification completes registration before returning to sign-in. Preserve the branded welcome animation and onboarding. Student remains the displayed student role. Source identity assets: `branding/bisu-logo.ico`, `app/src/main/res/drawable/bisu_logo.png`, and `univia_logo.png`.

## App identity and access

The confirmed app and project name is Univia App. The sign-in header says Univia. Email or username and password share one sign-in for all accounts; there is no Organization team access link or role chooser. Organization access is assigned to an existing account and remains available within the authenticated app. Onboarding introduces organization discovery, membership applications and community updates. Onboarding completion is stored locally. Keep the existing Firebase application ID and deployed data-service identifiers compatible with installed accounts.


## Public account security

Public accounts are Students using `bal_<schoolID>@bisu.edu.ph`, matching the
entered Student ID. Personal Gmail and lookalike domains are rejected. Users
verify a Firebase email link before entering app features. Pending profiles are
recoverable; the verification screen supports resend, refreshed status and
returning to sign-in. Data access trusts only Firebase's signed verified-email
claim. Coordinator accounts remain privately provisioned through the allowlist.
