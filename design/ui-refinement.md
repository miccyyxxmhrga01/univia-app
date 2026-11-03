# Univia interface refinement

## Scope and direction

Refine the existing native Kotlin/XML application. Keep Poppins, violet/orange identity, blue primary actions, BISU/Univia artwork, campus photography and sampled authentication glass. No frameworks, dependencies, backend logic, API contracts or role identifiers changed.

UI design, UX/accessibility, frontend implementation and QA were delegated to specialized agents. Implementation ownership was partitioned by file; the coordinator integrated dashboard, feed and discovery layouts. The working tree already contained extensive changes; those were retained.

## Changes

- Reduced resting shadows and centralized renderer surface, edge, focus, pressed and disabled colors. Preserved cached native drawing and existing photo-backed glass sampling.
- Respected declared card radii and synchronized runtime radius changes.
- Added shared spacing, touch-target and Poppins text-role resources.
- Let dashboard headers/navigation measure their content. Constrained screen content above the actual navigation height and anchored folder actions above it. The decorative student greeting yields space in windows shorter than 480dp.
- Aligned feeds with surrounding section gutters, protected content inside the painted surface and enlarged/distributed action targets.
- Added readable, scrollable 48dp organization filters and scalable search. Fixed white text on pale selected filters. Smart Match title, supporting copy and action now stack within the existing clickable card so enlarged text has room.
- Added merged navigation labels, selection/position semantics, native feedback, explicit password visibility state, visible sign-in progress and keyboard Done handling.
- Enlarged profile photo/edit/logout controls and removed a duplicate student-dashboard inflation.

## Screens and components

Student, Org Manager and Coordinator dashboard shells and overview content; shared text/photo feed cards; organization discovery filters/search; student profile actions; shared sign-in controls; shared soft surfaces used by cards, inputs and actions. Onboarding artwork uses the same resolved palette; its composition and routing remain intact.

## Files changed in this task

| Area | Files |
| --- | --- |
| Shared drawing | `app/src/main/java/com/example/kayara/CampusGlass.kt`, `NeumorphicDrawable.kt`, `NeoCardView.kt`, `OnboardingArtView.kt` |
| Interaction/accessibility | `app/src/main/java/com/example/kayara/UniviaNavigation.kt`, `StudentLoginActivity.kt`, `StudentDashboard.kt`, `BrowseOrgsFragment.kt` |
| Dashboard shells | `app/src/main/res/layout/activity_student_dashboard.xml`, `activity_admin_dashboard.xml`, `activity_super_admin_dashboard.xml` |
| Dashboard content | `app/src/main/res/layout/fragment_student_dashboard.xml`, `fragment_admin_dashboard.xml`, `fragment_dashboard.xml` |
| Feeds/discovery/profile | `app/src/main/res/layout/item_feed_text_post.xml`, `item_feed_photo_post.xml`, `fragment_browse_orgs.xml`, `fragment_profile.xml` |
| Tokens and copy | `app/src/main/res/values/colors.xml`, `ui_dimens.xml`, `ui_styles.xml`, `dashboard_dimens.xml`, `ui_accessibility_strings.xml`, `ui_layout_strings.xml`, `dashboard_layout.xml`, `app/src/main/res/values-h480dp/dashboard_layout.xml` |
| QA | `app/src/androidTest/java/com/example/kayara/UiRefinementTest.kt` |
| Documentation | `DESIGN.md`, `design/ui-refinement.md` |

## Verification

| Check | Executed result | Evidence |
| --- | --- | --- |
| Debug APK | Passed | `build/ui-refinement/final/final-build.log` |
| Android test APK | Passed | Same final build log |
| Unit tests | 8 passed, 0 failures/errors/skips | `app/build/test-results/testDebugUnitTest/` |
| Final complete native UI suite | 13 passed in 59.507 seconds | `build/ui-refinement/final/final-ui-tests.log` |
| Lint | 0 errors; 1,450 project warnings | `app/build/reports/lint-results-debug.html` |
| Resource XML | All 379 files parsed | Source verification |
| Whitespace | `git diff --check` passed | Source verification |

Build commands: `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest`, `:app:lintDebug`, using the existing wrapper and JDK 21. The final UI run used the installed API 37 image in an isolated headless emulator, serial `emulator-5580`. No physical phone was used.

The 13-test run includes all 5 new checks, the 5 existing brand/onboarding/sign-in checks, 2 registration checks and the account-access UI check. New coverage includes 12 dashboard render configurations (360x640dp with normal/130% text, 800x1280dp, and 800x360dp with 130% text), 320dp feed rows with long copy/counts, card radius/interaction rendering, discovery filter reachability and sign-in semantics/validation. The final Org Manager capture also asserts that Members stays on one line at 130% text. Configured-context renders verify native layout geometry; they are distinct from account-based end-to-end tests.

An additional actual-window sign-in run combining 130% system text, night mode and disabled animations was not completed after the session resumed: the temporary emulator was gone and the SDK emulator executable became unavailable. The configured 130% native renders and the clean 13-test run above remain verified. This extra configuration is a remaining device check, not a reported pass.

Visual review covered fresh phone/enlarged-text/tablet/short-window dashboards, discovery, feed rows, registration review, sign-in and all onboarding pages. The UI specialist confirmed the photo/glass identity and readable controls. Captures and geometry reports are under `build/ui-refinement/captures/`. That folder retains earlier iterations as well; the final Members adjustment is verified by the one-line assertion in the passing final suite, but its last capture was not retrieved before the temporary emulator disappeared. The photo-feed test image has no remote media fixture; it verifies the row and action geometry rather than network image loading.

Solid-color contrast calculations are at `build/ui-refinement/token-contrast.txt`: primary text 13.42:1, secondary 6.31:1, hints 6.28:1, selected filter text 10.65:1, white action labels at least 4.86:1 across normal gradient stops and 4.55:1 across pressed/disabled stops. These calculations do not certify every photographic pixel.

QA found and corrected navigation background measurement, short-window XML visibility and enlarged-text Smart Match/Members layout issues. The test inflater was corrected to retain the real AppCompat theme. Stale generated packaging/lint caches were resolved without dependencies or lint suppression. An initial pre-change build overlapped source edits and is not a valid baseline; original coordinator-owned layouts remain in `build/ui-refinement/source-before/layout/`. The earlier Espresso focus failure was traced to an emulator System UI ANR dialog; after clearing that external dialog, the final complete 13-test run passed. Earlier failed logs and the diagnostic screenshot `build/ui-refinement/captures/focus-window.png` are retained.

## Remaining recommendations

- Account-based end-to-end checks for production memberships, approvals, uploads, chat and every authenticated destination require appropriate test accounts/data.
- Add consistent inline loading, empty, retry and error states across authenticated lists in a separately bounded change.
- Restore list virtualization where long RecyclerViews are nested in scrolling containers.
- Extend tablet navigation and very large text/landscape behavior as native evidence requires; do not treat compact portrait checks as universal device coverage.
- Existing project-wide lint warnings and deprecated platform API usage need separate review.

## Context maintenance

The design skill flagged the existing PRODUCT.md as an older product-record schema. Its `init` workflow can refresh that separately; this task preserved the confirmed product context.
