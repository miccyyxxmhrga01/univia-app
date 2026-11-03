# Univia App campus glass interface

Native Android Views. The supplied login reference, preserved in `branding/login-design-reference.png`, governs layout and materials. Violet/orange accents and lavender glass remain; the latest request uses blue gradients for primary buttons. Combine frosted glass, soft neumorphic depth and restrained composition. Preserve the existing BISU and Univia App artwork and native workflows.

## Palette

Tokens live in `app/src/main/res/values/colors.xml`.

| Role | Value | Use |
| --- | --- | --- |
| Deep violet | `#3B176A` | Identity and headings |
| Violet | `#7C3AED` | Brand accents and selection |
| Action blue | `#1558BB` | Primary buttons and registration progress |
| Orange | `#F97316` | Accent rules, waves and illustration highlights |
| Canvas | `#F1ECF8` | App background |
| Glass base | `#FAF7FF` | Shared translucent surfaces |
| Recess | `#E7DEF4` | Inputs and selection |
| Ink | `#36204F` | Main text |
| Secondary ink | `#665672` | Supporting copy |
| Login secondary ink | `#301B43` | Supporting copy over photo-backed glass |
| Hint | `#5B476E` | Placeholders |
| Outline | `#D8C8EA` | Dividers |
| On primary | `#FFFFFF` | Labels on filled actions |
| Success | `#276653` | Approved/successful states |
| Error | `#AC3C4E` | Errors and destructive actions |

The application uses a deliberate light scheme even when Android night mode is enabled. Organization-specific artwork and stored organization identity colors remain content.

## Physical surfaces

`CampusSurfaces` paints pale lavender translucent gradients, soft violet shadows and white edge highlights. Primary actions use a blue gradient (`#103E8C` → `#1558BB` → `#0875C1`) with white labels, plus darker pressed and muted disabled states. Orange remains an accent. `NeumorphicDrawable` caches native renders per size and interaction state; input and primary shapes are pills. Pressed, focused, selected and disabled states remain visible. No bitmap blur runs per animation frame. Legacy blue/gold resource names alias the violet/orange brand tokens for compatibility; action blue is separate.

`NeoCardView` extends MaterialCardView so existing view contracts remain compatible. Layouts, cards, forms, sheets, dialogs, list items, navigation and common action backgrounds share the new palette and drawables. Material behavior, system Back, native input, error semantics, and existing backend workflows are retained. Touch targets were enlarged while keeping small icon artwork inside padded hit areas.

## Type and composition

Locally bundled Poppins regular, semibold and bold supply the reference's rounded lettering. Use scalable sp units. Pale lavender canvases and deep violet text carry the shared app design. Dashboard content is flexible with compact top bars and consistent bottom navigation. Keep controls and factual content clear rather than adding decoration to operational screens.

## Launch and onboarding

`WelcomeActivity` now shows the Univia App logo on a raised circular surface with the BISU seal and campus attribution. Alpha and GPU transforms reveal the raised base, lift the logo to a subtle 1.025 scale, and settle it to its natural size. Text and BISU attribution enter in sequence. A 700ms entrance, 200ms settle, and 300ms hold make a 1.2-second introduction at normal system speed. Android crossfades to the destination once its window is ready, keeping the logo visible while sign-in initializes. Navigation follows animation completion rather than an unrelated timer. Disabled system animation skips motion and delay. Backgrounding cancels the animation without navigation; resuming continues directly, and recreation does not replay it.

First use continues to `OnboardingActivity`: organization discovery, membership applications, and community announcements/events/chat. The same campus photograph, centered BISU/Univia App logo discs, orange/violet swoosh and translucent material carry through from login. One rounded glass panel contains the swipeable content and a fixed footer with progress and a blue-gradient Next/Get started pill. The page illustrations use the shared raised surfaces, violet icons and small orange accents; headings and copy are centered Poppins. Skip stays in the header. Native ViewPager2 supports swipe, Next, Skip and Get started. There is no visible Back button; Android system Back returns to the preceding page. Content scrolls independently of the footer on compact screens and with larger fonts. Height-specific resources resize the brand header and artwork. Page state survives recreation. Completion persists locally; later launches go to sign-in. Debug builds expose only the onboarding Activity for preview without clearing completion preferences; release routing is unchanged.

The confirmed app/project name is Univia App and the sign-in masthead reads Univia. One shared email-or-username/password form routes authenticated accounts according to their server permissions. The launch role chooser and Organization team access link are absent. Assigned organization management remains available after sign-in. The existing application ID, Firebase configuration, deployed API name and local onboarding preference key remain compatible with installed accounts.

The shared sign-in uses a full-bleed campus photo, centered BISU and Univia logo discs, white Univia wordmark, orange/violet swoosh and university attribution. The logo row begins 12dp lower, and the university line has an extra 6dp top gap. The wordmark is 24sp Poppins semibold throughout. A rounded frosted card contains the heading, subtitle, accent rule, credentials, password visibility, validation, blue-gradient sign-in pill and student registration link. The older role-specific login activities redirect to this form. There are no visible Back buttons; native system Back remains available. Height-specific spacing keeps the composition on compact and taller phones. The form scrolls above the keyboard and with larger fonts.

`CampusBackdropDrawable` reads the original campus image from `drawable/login_bg.jpg` without density rescaling. The photo fills the full scene, with a wider upper crop blended over it to keep the photographed BISU seal visible above the form. The caption is cropped. At the user's request, the white background fade is confined to the bottom 20% of the screen, beginning at 80% of its height. There is no full-screen color wash above that boundary. The fade reaches white at the top of an opaque system navigation bar, which continues white to the screen bottom; it must not finish hidden underneath that bar. Background and frosted caches share the same inset-adjusted endpoint. `CampusGlassCard` samples a more strongly blurred version of the backdrop at its current window position and clips its children to the rounded silhouette. `CampusLoginCard` is a compatibility subclass for existing login layouts. The lavender-white glaze uses 32–44% opacity with a subtle 13% reflection, a white perimeter and violet/orange waves. Dark supporting ink maintains readability. The sampled backdrop follows scrolling and keyboard changes; text and controls stay sharp. Both scene and frost are cached, with the frost scene at half resolution to reduce memory use. No blur runs per frame.

The user's follow-up crop is preserved in `branding/login-glass-detail-reference.png`. The background uses the original photo bytes (SHA-256 verified), renamed with an underscore for Android. Card scroll invalidation updates its sampled scene even when Android reuses hardware display lists. Follow-up validation: debug APK and lint passed with zero lint errors; credential validation and password visibility passed for all three logins on API 37. On a compact 360dp display with 130% text, the login and actual scroll/tap team-access checks both passed. A hardware-rendered scrolled frame was also visually checked. Captures and reports are under `build/glass-card/`; the final APK is `app/build/outputs/apk/debug/app-debug.apk`. The physical phone disconnected before the final follow-up reinstall.

Glass visibility correction: the full-height photographic layer and reduced tint now expose the backdrop throughout the panel. Supporting copy and the team link were darkened after sampling contrast in the rendered result. The debug/test APK builds and lint passed with zero errors; credential/password checks passed on all three login screens, including the compact 130% text configuration. The team-access tap check missed a tap during the dialog animation, then passed with test-window animations disabled. Current captures and reports are under `build/clear-glass/`. The corrected APK was installed and launched on the realme; its on-device SHA-256 matched the local build.

## Registration flow

Student registration has five steps: Profile, Account, Interests, Review and Verify email. Organization registration has Organization, Account, Documents and Review. A numbered tracker and explicit step label stay above the scrollable fields. Current, completed and upcoming steps have distinct states; completed form steps can be tapped to edit. Continue validates only the current step. The final review action saves a pending profile and opens email verification immediately. Registration is complete only after the server confirms the profile and verified email; the app then signs out and returns to sign-in with the email filled in and a completion message. An interrupted registration can resume its final verification step from sign-in. No account or document upload is initiated by the intermediate Continue buttons.

Both flows use the same campus photograph, paired centered logos, lavender frosted card, recessed labeled inputs and blue gradient actions. The review summarizes the entered details and document labels without exposing passwords. Android Back and the visible back arrow return to the previous step. The primary action stays below the scrolling fields. Compact height and the keyboard hide the decorative header, subtitle and sign-in link to leave room for editing.

Ordinary fields and the current step survive recreation; passwords survive rotation in a memory-only ViewModel and are excluded from saved state. After process loss, a later step returns to Account to re-enter credentials. Organization files use the native OpenDocument picker with persisted read access when supported; selected URIs, labels and the current picker slot survive recreation. The existing Cloudinary upload begins only on final submission. Navigation is disabled while the existing submission request is busy.

## Insets and accessibility

Shared inset handling keeps content clear of system bars and the keyboard. Login has transparent status bars and white icons over the photo; other screens use dark icons on the light surface. Floating dialogs retain native window behavior. Labels accompany credentials, password controls remain accessible, errors use a live region, onboarding progress announces the current page and illustration views are decorative. System animation settings are honored.

## Asset provenance

The user-supplied BISU seal is preserved in `branding/bisu-logo.ico`, with its Android PNG at `drawable/bisu_logo.png`. `drawable/univia_logo.png` is the existing app artwork, also used for launcher derivatives. The supplied reference is `branding/login-design-reference.png`. The original campus photo is preserved in `branding/login-bg.jpg`, with a byte-identical Android copy at `drawable/login_bg.jpg`. Poppins comes from the official Google Fonts repository, with its OFL license and attribution in `branding/fonts/`. Waves, rules and outlined field icons are native vectors/Canvas geometry. No generated logo or university seal is introduced.

## Shared interface refinement (November 2026)

The established Poppins, violet/orange identity, lavender surfaces, blue action gradients, campus photograph and sampled authentication glass remain authoritative. Resting surfaces use a restrained upper-left light source: a 15% violet shadow, 4dp blur, 1.5dp horizontal and 3dp downward offset. Primary actions retain slightly stronger depth. Pressed and disabled controls release the raised shadow. Focus has a visible violet perimeter and is distinct from recessed selection. Surface, edge, pressed, disabled and focus colors resolve from shared resource tokens once per drawable; bitmap caching remains intact.

`NeoCardView` honors the radius declared by each existing layout and keeps runtime radius changes synchronized with its background. Reusable operational-screen typography and 4dp spacing tokens live in `ui_styles.xml` and `ui_dimens.xml`; authentication retains its reference-specific dimensions. Dashboard section titles, greetings and feed body copy use shared Poppins roles. Feed cards align with their host section gutters, protect content inside the 7dp painted-surface inset and distribute the three action targets evenly.

The three dashboard shells measure their masthead and bottom navigation from content with an 80dp minimum, then constrain content between them. Navigation labels can wrap with larger text without relying on a fixed 98dp content spacer. In windows shorter than 480dp, the noninteractive student greeting yields its space to task content; normal-height windows retain it. Each destination exposes one complete accessible label, selected tab/position semantics and native interaction feedback. Organization filters scroll horizontally on compact screens, keep 48dp targets and dark readable labels on pale selected surfaces. The Smart Match card stacks supporting copy and its action below the title so larger text retains a useful measure. Profile actions also have 48dp minimum targets. Sign-in exposes its button role, visible busy label, password visibility state and keyboard Done action while keeping the existing authentication path.

Current refinement verification: final debug and test APK builds passed; all 8 unit tests and all 13 native UI tests passed on API 37. Lint has zero errors and 1,450 project warnings. Exact scope, evidence and outstanding limits are recorded in `design/ui-refinement.md`; older verification paragraphs below describe earlier revisions.

## Verification and limits

Univia identity revision: the app label, project metadata, welcome text, dashboard branding, logo resource names, shared UI helpers and theme names now use Univia App. Sign-in shows Univia, with the logo row 12dp lower and an additional 6dp gap above the university subtitle. The removed team-access label is absent from app resources; shared sign-in and authenticated organization management remain intact. Debug and test APK builds, four unit checks, all seven interface checks and two compact/130%-text sign-in checks passed. Lint reported zero errors and 1,415 project warnings. The build was installed on the realme, its SHA-256 matched the local APK, and the login header was visually reviewed. Captures and reports are under `build/univia-rename/`. The project folder rename is awaiting release of Windows file locks from the open IDE/terminals.

Registration flow revision: debug APK and test APK builds passed; final lint reported zero errors (1,413 warnings across the project). Student and organization flow tests passed at 360×800dp with normal text and 360×640dp with 130% text. Checks cover per-step validation, password visibility, completed-step editing, native Back, review privacy, rotation and all five selected organization documents. The file picker was intercepted with harmless local fixtures; no accounts, uploads or applications were submitted. Existing login credential/password and introduction/completion regression checks passed. The actual Android keyboard was visually checked: branding recedes, scrollable fields remain usable and Continue stays above the keyboard. White labels have at least 4.86:1 contrast against every blue gradient stop. Final captures, build and test logs are under `build/registration-flow/`; the APK remains `app/build/outputs/apk/debug/app-debug.apk`. No physical phone was connected during this revision.

Violet/orange color trial: 43 theme source files were updated across shared resources, native gradients, vector accents, programmatic UI colors and default model colors. No old blue/gold UI hex literals remained in the source audit. Debug APK/test APK builds and lint passed with zero errors. All six UI checks passed individually; the initial team-access run did not find the dialog root and passed on its isolated retry. Login, onboarding and dashboard renders were visually reviewed. White action labels have at least 5.18:1 contrast against every gradient stop. The new APK was installed on the realme and its SHA-256 matched the local build. Current captures and reports are under `build/violet-orange/`; originals are retained under `build/violet-orange/before/` for this trial. Earlier screenshots document the previous blue/gold palette.

Login photo framing refinement: the login background uses a 0.76 framing scale around the campus seal, with a minimum coverage clamp to avoid empty edges. Onboarding retains its 1.0 framing. The clear and frosted layers share the same transform, while the white fade still begins at 80% of the screen height. The final debug build passed and was installed and visually reviewed on the realme. Captures and build reports for the successive framing adjustments are under `build/login-zoom/`, with the final phone capture at `build/login-zoom/final.png`.

Bottom-fade clarification: removed the remaining full-screen photographic tint. On the realme's 720×1600 screen, the white fade starts at y=1280, reaches white just above its navigation bar at y=1512 and continues white to y=1600. The upper 80% has no background color wash. Both the photo and frosted sampling cache respond to navigation-inset changes. Debug build and lint passed with zero errors. The installed APK hash matched the local build, and the unlocked phone's login screen was visually checked; before/after captures and the build report are under `build/login-bottom-fade/`.

Onboarding and white-fade revision: debug APK/test APK builds and lint passed with zero errors (existing project warnings remain). All six device checks passed on the taller API 37 configuration. The final compact 360dp configuration at 130% text passed onboarding completion/returning launch, Skip and the credential/password checks for all three logins. All three onboarding pages were visually reviewed; page copy scrolls independently when the font size needs more space, with the action footer always accessible. The background fade was checked from its 80% start through a pure white bottom edge. The debug preview export and private release Activity were verified in the merged manifests. Reports and captures are under `build/glass-onboarding/`. The final APK was installed on the realme using its connected wireless adb session, verified by matching its SHA-256, and onboarding was launched directly without resetting first-run preferences.

`BrandUiSmokeTest` covers introduction routing, onboarding completion and returning launch, Skip, team sign-in access, credential validation/password controls, and dashboard rendering/navigation. Native captures and reports for this reference revision are under `build/glass-reference/`. Signed-in Firestore approvals, memberships, uploads and production data require account-based end-to-end testing.

Finish review: build, template unit test, and lint passed with zero lint errors (warnings remain). All six device tests passed on API 37, including a 360dp phone configuration with 130% text, system night mode, and disabled animations. Welcome and onboarding were also installed, launched, and visually checked on the connected realme RMX2180 running API 30. Native screenshots are retained under `build/neumorphic/`. The subsequent login-card revision passed the build, lint, and all six device tests, and was visually checked on the realme; its captures are under `build/login-card/`. The logo-motion revision passed build/lint and the onboarding/returning-launch smoke test on the realme, with a reduced-motion path also checked during development. Its reports are under `build/logo-motion/`.

Reference revision finish review: the campus backdrop, paired logos, white campus lettering, outlined frosted card, pill fields, blue action and blue/gold waves follow the supplied composition. Height-specific resources preserve it on compact and taller phones. Registration and team access stay inside the card; the extra onboarding replay link was removed to match the reference. Shared typography, palette and glass surfaces carry through the app.

`completed-build.log` records successful debug/test APK builds, unit tests and lint with zero errors (1,441 warnings remain across the project). All six emulator checks passed on API 37, including a compact 360dp display with 130% text and the final taller 360dp configuration. The team-access test now scrolls and taps the actual control through Espresso so window focus is synchronized when returning from another login. Final captures are under `build/glass-reference/final-screenshots/`. The final APK was installed and launched on the realme API 30 device; its physical tap check was limited by a System UI window retaining focus during USB connection, while the equivalent emulator tap paths passed. The temporary physical test package was removed.


## Coordinator-assigned organization access

The shared sign-in keeps the current brand and native form styling. Every new
account is a Student; sign-in has no role selector or team access sheet. The server
routes provisioned coordinators to their dashboard. Students enter organization
management through a permission-controlled action on Profile, with Student view
available from the manager header.

Organizations > Manage organization access lets a coordinator choose an
organization, search by school ID or email, inspect the matching account, and
confirm assignment, replacement or removal. The screen includes loading, empty,
lookup ambiguity, connection failure, retry and stale-assignment states. It uses
native Material controls with 48dp minimum targets and a scrolling form. Manager
assignments are scoped to one organization; a person may manage several through
the same account. Privileged controls wait for server confirmation and close when
access is revoked. No local role preference can grant permission.

Organization applications are submitted from the signed-in Student profile.
The native form retains the five document requirements, status history, rejection
reasons and revision. Upload work and selected files survive rotation. Approval
creates an unassigned organization and leaves the Student profile intact. The
coordinator assigns its manager separately. No new image assets were introduced.


## Email verification

Public Student registration retains the exact BISU domain and Student ID match.
The account step and final review explain that email verification is required.
Verification is the fifth Student registration step, before the first completed sign-in.
The screen labels this as finishing registration. A successful Firebase email send
is converted to a non-null completion result so the callback cannot crash on
Firebase's empty success value. Verification codes remain supported when available;
Firebase links work with the existing deployed API.
The verification screen uses the existing native typography, pale surface,
outlined mail icon and Material controls. It displays the signed-in email address,
a clear inbox instruction, send/check status, verification check, email-app entry,
resend cooldown and sign-out. The form scrolls with larger text. Verification
refreshes after returning from email, and resend requests survive rotation without
duplicating sends. No new image assets were introduced.
