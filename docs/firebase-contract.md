# Firebase Contract

This document defines the Firebase-side contract for the PartPlan desktop app before any Firebase implementation code is added.

## Scope

- Replace all runtime-local persistence with Firebase-backed auth, Firestore, and Storage.
- Keep all user data scoped by Firebase Auth `uid`.
- Preserve the existing plan lifecycle:
  - `PENDING` plans are editable
  - `COMPLETE` plans are read-only
  - inspections can only be created from `COMPLETE` plan versions
- Preserve the existing lot lifecycle:
  - lots reference an exact completed plan version
  - deleting a plan version deletes all lots under that exact plan version
  - upversioning a lot preserves measurements by stable bubble `id`

## Client configuration

The desktop app may store Firebase client configuration locally.

Allowed local config:

- `apiKey`
- `projectId`
- `storageBucket`
- `appId`
- `authDomain` if needed

Allowed local session data:

- `uid`
- `email`
- `idToken`
- `refreshToken`
- `expiresAt`

Do not store locally:

- service account JSON
- private keys
- Admin SDK credentials

Reasoning:

- Firebase Auth REST and Firestore REST support acting as the signed-in user.
- Embedding admin credentials would bypass the user-scoped security model.

## Authentication contract

The JavaFX client will use the Firebase Auth REST API.

Sign up:

- `POST https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={apiKey}`

Sign in:

- `POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={apiKey}`

Refresh session:

- `POST https://securetoken.googleapis.com/v1/token?key={apiKey}`
- form body:
  - `grant_type=refresh_token`
  - `refresh_token={refreshToken}`

Expected session mapping:

- `localId` -> `uid`
- `idToken` -> bearer token for Firestore user requests
- `refreshToken` -> persisted refresh token
- `expiresIn` / `expires_in` -> derive `expiresAt`

`AuthService` will eventually support:

- `signIn(email, password)`
- `signUp(email, password)`
- `signOut()`
- `refreshSession()` or equivalent internal refresh behavior

`SessionManager` remains the single source of truth for the current authenticated user.

## Firestore model

Use document-oriented, user-scoped paths.

Top level:

- `users/{uid}`

Plan family:

- `users/{uid}/planFamilies/{familyId}`

Suggested fields:

- `name`
- `partNumber`
- `latestCompleteVersion`
- `latestCompletePlanId`
- `activeDraftPlanId`
- `createdAt`
- `updatedAt`

Exact plan version:

- `users/{uid}/planFamilies/{familyId}/versions/{planId}`

Suggested fields:

- `familyId`
- `name`
- `partNumber`
- `revision`
- `description`
- `version`
- `status`
- `createdAt`
- `updatedAt`
- `completedAt`

Plan pages:

- `users/{uid}/planFamilies/{familyId}/versions/{planId}/pages/{pageId}`

Suggested fields:

- `name`
- `pageNumber`
- `fileName`
- `fileType`
- `storagePath`
- `createdAt`
- `updatedAt`

Plan bubbles:

- `users/{uid}/planFamilies/{familyId}/versions/{planId}/bubbles/{bubbleId}`

Suggested fields:

- `pageId`
- `x`
- `y`
- `radius`
- `useDefaultDiameter`
- `color`
- `useDefaultColor`
- `label`
- `characteristic`
- `inspectionType`
- `nominalValue`
- `lowerTolerance`
- `upperTolerance`
- `expectedPassFail`
- `note`
- `sequenceNumber`
- `createdAt`
- `updatedAt`

Important rule:

- `bubbleId` is the stable internal identity.
- Upversioned lots preserve measurements by `bubbleId`, not by sequence number or label.
- A bubble can move from displayed number `12` to `8` between versions and still map correctly if its `bubbleId` stays the same.

Inspection lot:

- `users/{uid}/inspectionLots/{lotId}`

Suggested fields:

- `name`
- `planId`
- `planFamilyId`
- `planName`
- `planVersion`
- `lotSize`
- `createdAt`
- `updatedAt`

Lot parts:

- `users/{uid}/inspectionLots/{lotId}/parts/{partId}`

Suggested fields:

- `partNumber`
- `measurements`

`measurements` is a map:

- key = `bubbleId`
- value = measurement string

No duplicated stored bubble definition collection under the lot.
Bubble definitions are always derived from the referenced completed plan version.

## Storage model

Use user-scoped object paths.

Canonical page assets:

- `users/{uid}/planFamilies/{familyId}/versions/{planId}/pages/{pageId}/{fileName}`

The page document stores the Storage object path in `storagePath`.

Current rule:

- rendered page image is the canonical editor asset
- if the user imports a PDF, the client renders pages locally and uploads the rendered page images

Optional later enhancement:

- store original PDF under a separate `sources/` object path

## Repository mapping

Current repositories map to Firebase as follows:

- `PlanRepository`
  - backed by Firestore plan family/version docs and Storage page assets
- `LotRepository`
  - backed by Firestore lot docs and lot-part docs
- `AssetStore`
  - backed by Cloud Storage
- `AuthService`
  - backed by Firebase Auth REST

## Delete semantics

Deleting a plan version is normal app behavior.

When deleting plan version `planId`, also delete:

- plan version page docs
- plan version bubble docs
- Storage objects for all pages under that version
- all inspection lots where `lot.planId == planId`
- all part docs under those lots

Plan-family behavior:

- deleting version `v2` does not delete other versions in the same family
- deleting the latest complete version must update `planFamilies/{familyId}` summary fields
- if no versions remain, delete the family doc too

Important Firebase note:

- Firestore does not automatically cascade deletes through subcollections or sibling collections.
- The app or a trusted backend must explicitly delete the related documents and storage objects.

Preferred production approach:

- use a trusted backend or callable function for recursive delete

Acceptable first pass:

- client performs an ordered delete for the user’s own data using authenticated user tokens

## Lot upversion semantics

Lot upversion is allowed only when:

- the target plan is `COMPLETE`
- the target plan belongs to the same `familyId`
- the target plan version is newer than the lot’s current `planVersion`

When upversioning a lot:

1. load the target plan version
2. derive the target bubble definitions from that plan
3. preserve existing measurements where `bubbleId` still exists
4. create blank measurements for new `bubbleId`s
5. drop measurements for removed `bubbleId`s
6. update the lot’s `planId`, `planVersion`, and `planName`

This is exactly why the bubble’s stable internal `id` matters.

## Firestore rules contract

Baseline access rule:

- users can only access their own subtree

Additional behavioral rules:

- completed plan versions are immutable after creation
- lot writes must reference a completed plan version in the same user subtree

Rules should protect data ownership and obvious invariants.
More complex delete/upversion workflows are better enforced in repository/service logic and, later, trusted backend operations.

## Storage rules contract

Baseline access rule:

- users can only access files under their own `users/{uid}/...` prefix

No shared anonymous asset access is required for this app.

## Implementation sequence

1. Add local storage for Firebase project config
2. Implement Firebase Auth REST sign-in/sign-up/refresh
3. Add login UI and session restore behavior
4. Implement Firestore-backed `PlanRepository`
5. Implement Firestore-backed `LotRepository`
6. Implement Storage-backed `AssetStore`
7. Replace the in-memory repositories in `AppContext`
8. Delete dead local storage classes from the repo

## Open decisions

1. Whether plan deletion cascade runs in the client first or through a trusted backend from day one.
2. Whether original imported PDFs are stored in Storage or only the rendered page images.
3. Whether lot measurement values remain strings or are normalized by inspection type later.

## Source notes

This contract is based on official Firebase docs for:

- Auth REST sign-in/sign-up/refresh
- Firestore REST with Firebase ID tokens
- Firestore data model and subcollection delete behavior
- recommended recursive delete approach
- Firebase Auth-backed Firestore and Storage security rules
