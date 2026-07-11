# Unified Identity — Android Integration Guide

**Status:** new backend (migration 035) is committed but **not yet deployed**. Read this whole doc before integrating; nothing on Android needs to change until the migration is live on Railway. After deploy, the existing Android auth (magic-link and teacher-device) continues to work — the new endpoints are **additive**, not breaking.

**Audience:** the agent owning `seenslide-android`.

---

## 1. What changed on the backend

The cloud now supports **three identifiers** for a single account:

| Identifier | Required for | Notes |
|---|---|---|
| `device_id` | Anonymous accounts | Bootstraps automatically on first launch; no user input required |
| `email` | Email-based account | UNIQUE when set; required if using email magic-link recovery |
| `phone_number` | Phone-based account | UNIQUE when set; no SMS verification — secret gates the account |

A `viewer_users` row may have any combination. Anonymous = no email/phone/secret, only attached devices. Claimed = secret + at least one of (email, phone).

A new table `user_devices(device_id UNIQUE, user_id, ...)` tracks every device attached to an account. One device → exactly one user. One user → many devices.

**Crucially:**
- `cloud_sessions.user_id` is now the canonical owner. `presenter_email` is legacy/redundant.
- `GET /api/cloud/sessions` is now **bearer-auth required and filters by `user_id`** — it returns *only* the caller's sessions. (Previously returned all globally.) Android already sends bearer; should just work.
- `POST /api/cloud/session/create` is now **bearer-auth required** and stamps `user_id` from the bearer. Android already sends bearer; should just work.

**The legacy magic-link and teacher-device endpoints are untouched.** Existing Android flows keep working as-is.

---

## 2. New endpoints (the contract)

All under `https://seenslide.com`. All accept and return `application/json`. Bearer auth via `Authorization: Bearer <token>` header (where required).

### 2.1 `POST /api/auth/device-bootstrap`

First-launch path. Idempotent — calling it again with the same `device_id` returns the existing account with a fresh session token.

**Auth:** none.

**Request:**
```json
{
  "device_id": "stable-os-machine-id-or-uuid",
  "device_label": "Pixel 7 (Android)"   // optional, max 100 chars
}
```

**Response 200:**
```json
{
  "session_token": "Xy7…",                  // 48-char URL-safe; use as Bearer
  "user": {
    "user_id": "uuid",
    "email": null,                          // null while anonymous
    "phone_number": null,
    "full_name": null,
    "is_anonymous": true,
    "account_tier": "free"
  }
}
```

### 2.2 `POST /api/auth/claim`

Attach an email or phone + secret to the device's anonymous account. Three outcomes, encoded in `action`:
- `"upgraded"` — identifier was new; anonymous user was promoted in place. **`user_id` unchanged**, all data intact.
- `"merged"` — identifier already exists with a matching secret; the anonymous account was merged into the existing user. **`user_id` changed** to the existing user's id; the device was reparented; sessions from the anon account were transferred to the existing user.
- `"login"` — same identifier was already attached to this device's user (idempotent re-claim).

**Auth:** none. (The device_id authenticates the anon side; the secret authenticates the existing-user side.)

**Request:** exactly one of `email` or `phone_number`.
```json
{
  "device_id": "…",
  "email": "user@example.com",        // or phone_number, not both
  "phone_number": null,
  "secret": "123456"                  // 4–128 chars; recommend 6-digit PIN for phone path
}
```

**Response 200:**
```json
{
  "session_token": "…",
  "user": { /* IdentityUserPayload, see 2.6 */ },
  "action": "upgraded" | "merged" | "login"
}
```

**Errors:**
- `400` — missing / both identifiers, no secret, etc.
- `401` — identifier exists but secret is wrong (failed-attempt counter increments)
- `409` — anonymous account is already claimed (caller used wrong endpoint; should call `/login` instead)
- `429` — account locked (returned with `Retry-After`-equivalent message after 10 failed attempts → 15 min lockout)

### 2.3 `POST /api/auth/login`

Attach a fresh device to an existing identifier-keyed account.

**Auth:** none.

**Request:** exactly one of `email` or `phone_number`.
```json
{
  "device_id": "…",
  "device_label": "Pixel 7 (Android)",   // optional
  "email": "user@example.com",
  "phone_number": null,
  "secret": "123456"
}
```

**Response 200:**
```json
{
  "session_token": "…",
  "user": { /* IdentityUserPayload */ }
}
```

**Special behavior:** if the device was already attached to a *different* anonymous user, that anonymous user's sessions are transferred to the logged-in user (same as a merge). This handles the case where a user used the device anonymously, then signed in to an account they already had elsewhere.

**Errors:** `401` for wrong identifier or wrong secret (intentionally indistinguishable to avoid leaking which identifiers exist); `429` for lockout.

### 2.4 `POST /api/auth/me/identifiers`

Edit email, phone, and/or secret. **Authenticated.** Current secret required for claimed accounts.

**Auth:** `Authorization: Bearer <token>`.

**Request:** any subset of fields. Pass an empty/omitted field to leave it unchanged.
```json
{
  "current_secret": "old-pin",      // required for claimed accounts
  "new_email": "new@example.com",   // optional
  "new_phone": "+8801XXXXXXXXX",    // optional
  "new_secret": "654321"            // optional
}
```

**Response 200:** `IdentityUserPayload` (see 2.6).

**Errors:** `401` wrong current_secret; `409` new identifier already in use by someone else.

### 2.5 `POST /api/auth/recover`

Request a magic link to reset the secret. **Email-only path** (phone-only users have no automatic recovery — they must use another attached device or contact support).

**Auth:** none.

**Request:**
```json
{ "email": "user@example.com" }
```

**Response 200:** `{ "sent": true | false }`.
We always return 200 (no info leak about whether an email is registered).

### 2.6 `GET /api/auth/me/identity`

The richer `/me` payload: includes `phone_number` and `is_anonymous`, which the existing `/api/auth/me` does not.

**Auth:** `Authorization: Bearer <token>`.

**Response 200 (`IdentityUserPayload`):**
```json
{
  "user_id": "uuid",
  "email": "user@example.com",
  "phone_number": "+8801XXXXXXXXX",
  "full_name": "Faisal",
  "is_anonymous": false,
  "account_tier": "free"
}
```

---

## 3. Recommended Android changes

The legacy `TeacherAuthApi` flow (phone + PIN + device-id via `register-device`) can stay for now. Treat it as deprecated; new code should use the unified `/api/auth/*` endpoints. The eventual goal is: every new install runs `device-bootstrap` and only later optionally `claim`s with email or phone. Migrate at your pace.

### 3.1 New file: `IdentityApi.kt`

Add a new Retrofit interface alongside `AuthApi.kt` and `TeacherAuthApi.kt`:

```kotlin
package com.seenslide.teacher.core.network.api

import com.seenslide.teacher.core.network.model.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface IdentityApi {

    @POST("api/auth/device-bootstrap")
    suspend fun deviceBootstrap(@Body request: DeviceBootstrapRequest): DeviceBootstrapResponse

    @POST("api/auth/claim")
    suspend fun claim(@Body request: ClaimRequest): ClaimResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: IdentityLoginRequest): IdentityLoginResponse

    @POST("api/auth/me/identifiers")
    suspend fun updateIdentifiers(@Body request: UpdateIdentifiersRequest): IdentityUserPayload

    @POST("api/auth/recover")
    suspend fun recover(@Body request: RecoveryRequest): RecoveryResponse

    @GET("api/auth/me/identity")
    suspend fun getMyIdentity(): IdentityUserPayload
}
```

### 3.2 New file: `IdentityModels.kt`

Mirror these to the JSON above. Use Moshi `@JsonClass(generateAdapter = true)` to match the rest of the project.

```kotlin
data class DeviceBootstrapRequest(val device_id: String, val device_label: String? = null)

data class IdentityUserPayload(
    val user_id: String,
    val email: String?,
    val phone_number: String?,
    val full_name: String?,
    val is_anonymous: Boolean,
    val account_tier: String,
)

data class DeviceBootstrapResponse(val session_token: String, val user: IdentityUserPayload)

data class ClaimRequest(
    val device_id: String,
    val email: String? = null,
    val phone_number: String? = null,
    val secret: String,
)

data class ClaimResponse(
    val session_token: String,
    val user: IdentityUserPayload,
    val action: String,   // "upgraded" | "merged" | "login"
)

data class IdentityLoginRequest(
    val device_id: String,
    val device_label: String? = null,
    val email: String? = null,
    val phone_number: String? = null,
    val secret: String,
)

data class IdentityLoginResponse(val session_token: String, val user: IdentityUserPayload)

data class UpdateIdentifiersRequest(
    val current_secret: String? = null,
    val new_email: String? = null,
    val new_phone: String? = null,
    val new_secret: String? = null,
)

data class RecoveryRequest(val email: String)
data class RecoveryResponse(val sent: Boolean)
```

### 3.3 Device-id resolver

Stable, persisted across launches. Use Android's `Settings.Secure.ANDROID_ID` as the seed and pin it to encrypted prefs / DataStore so it never changes even if Android wipes/regenerates the system value.

```kotlin
@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore, // see 3.4 — extend to store device_id
) {
    suspend fun get(): String {
        tokenStore.deviceId.first()?.let { return it }
        val seed = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()
        // Salt with package name to namespace it.
        val deviceId = "${seed}-${context.packageName}".take(64)
        tokenStore.setDeviceId(deviceId)
        return deviceId
    }
}
```

> **Note:** `ANDROID_ID` resets on factory reset and on app reinstall on Android 8+. That's acceptable — a fresh device legitimately needs to bootstrap a new account. Persisting our own copy is what keeps the id stable across normal app upgrades.

### 3.4 Extend `TokenStore`

Add `deviceId` and `userIsAnonymous` keys:

```kotlin
private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
private val KEY_IS_ANONYMOUS = booleanPreferencesKey("is_anonymous")

val deviceId: Flow<String?> = context.authDataStore.data.map { it[KEY_DEVICE_ID] }
val isAnonymous: Flow<Boolean> = context.authDataStore.data.map { it[KEY_IS_ANONYMOUS] ?: true }

suspend fun setDeviceId(value: String) {
    context.authDataStore.edit { it[KEY_DEVICE_ID] = value }
}

suspend fun saveIdentity(token: String, user: IdentityUserPayload) {
    context.authDataStore.edit {
        it[KEY_AUTH_TOKEN] = token
        it[KEY_USER_ID] = user.user_id
        it[KEY_USER_EMAIL] = user.email ?: ""
        it[KEY_USER_NAME] = user.full_name ?: ""
        it[KEY_IS_ANONYMOUS] = user.is_anonymous
    }
}
```

### 3.5 App-startup bootstrap

In whatever sits at the top of `MainActivity` / `App` (likely a splash or onboarding launcher), call `IdentityApi.deviceBootstrap` if `tokenStore.authToken` is null. Persist the returned token via `saveIdentity`. Block the rest of the app behind that one call. On failure (offline) you can either show a retry screen or proceed with a degraded mode — your call.

```kotlin
suspend fun ensureBootstrap() {
    if (tokenStore.authToken.first() != null) return
    val deviceId = deviceIdProvider.get()
    val resp = identityApi.deviceBootstrap(
        DeviceBootstrapRequest(device_id = deviceId, device_label = deviceLabel())
    )
    tokenStore.saveIdentity(resp.session_token, resp.user)
}

private fun deviceLabel(): String =
    "${Build.MANUFACTURER} ${Build.MODEL}".trim()
```

### 3.6 Replace `register-device` with `claim` in onboarding (eventually)

The current onboarding gates the user behind phone + PIN registration before they can do anything. Replace that with:

1. **Always boot anonymous** (3.5 above). Now the user can use the app immediately.
2. **Offer "Sign In or Register" as a non-blocking action** in profile/settings.
3. When the user submits email/phone + PIN there, call `IdentityApi.claim(...)` with the device's id and the secret.
4. Inspect `action` in the response:
   - `upgraded` → show "Account created" / "Email registered"
   - `merged` → show "Welcome back — sessions transferred from this device to your existing account"
   - `login` → idempotent, treat like a successful login

### 3.7 Sign-in for an existing user on a fresh device

Use `IdentityApi.login`. The server will reparent any anonymous sessions on this device into the logged-in user automatically.

### 3.8 Profile editing

`IdentityApi.updateIdentifiers(...)`. Pass only the fields the user changed; pass `current_secret` (the existing PIN/password) for confirmation.

### 3.9 Recovery flow

`IdentityApi.recover(email)`. Show a "check your email" screen. Server returns a magic link that, when clicked in a browser, resets the secret via the existing magic-link flow. (No in-app deep link required for v1.)

---

## 4. Backward compatibility

| Existing Android code | After migration 035 | Action |
|---|---|---|
| Magic-link login (`AuthApi.requestMagicLink` + `verifyToken`) | Still works | Keep — useful as recovery |
| Teacher device register (`TeacherAuthApi.registerDevice`) | Still works | Keep working until 3.6 ships, then deprecate |
| `GET /api/cloud/sessions` | Now bearer-required, user-filtered | No change needed; you already send bearer |
| `POST /api/cloud/session/create` | Now bearer-required, owner-stamped | No change needed |
| `presenter_email` field | Now redundant with `user_id` | Keep sending; will be ignored after a future cleanup |

---

## 5. Sequencing (what to do, in order)

1. **Wait for backend deploy.** The user will run migration 035 on Railway and ship the auth-router server changes. Confirm before testing.
2. **Add `IdentityModels.kt` + `IdentityApi.kt`** (sections 3.1–3.2). Wire the Retrofit interface in your DI module (probably `Koin.kt` or equivalent).
3. **Implement `DeviceIdProvider`** (3.3) and extend `TokenStore` (3.4).
4. **Wire `ensureBootstrap()` into app startup** (3.5). At this point, every new install gets an anonymous bearer, and `GET /api/cloud/sessions` returns an empty list (correctly).
5. **Add a Sign-In / Register screen** that calls `claim` (3.6). Test all three branches: new identifier (upgraded), existing identifier with right secret (merged), existing identifier with wrong secret (401).
6. **Add a Login screen for fresh devices** (3.7).
7. **Add profile editing** (3.8).
8. **Add recovery** (3.9). Optional in v1.
9. **Once 3.6 ships and you're confident**, remove the phone-required onboarding gate and treat the legacy `TeacherAuthApi` as deprecated.

---

## 6. Things to be careful about

1. **Lockout state lives on the user.** A single user gets locked after 10 wrong attempts within the lifetime of the account (counter clears on success). If you build retry UI, surface the `429`/lockout message clearly so users understand.
2. **`merged` changes `user_id`.** Anything keyed by user_id locally (cached session list, viewer cache, ML personalization, etc.) needs to be invalidated when `claim` returns `action == "merged"`. Treat it as "fresh user" for client-side state.
3. **Phone with no SMS verification means phones are self-asserted.** Anyone can claim an unused phone number with their own secret. The damage is limited (the secret is the actual gate), but if you display phone number prominently anywhere, frame it as "phone number" not "verified phone number."
4. **Device-id stability.** If you change how `DeviceIdProvider` derives the id, every existing anonymous install gets re-bootstrapped as a brand-new account on next launch. Lock the algorithm before shipping.
5. **`/api/auth/me/identity` vs `/api/auth/me`.** The legacy `/api/auth/me` does not include `phone_number` or `is_anonymous`. Use the new endpoint for the unified flow; the old one is fine for code paths you're not touching.

---

## 7. Quick contract reference (cheat sheet)

```
POST /api/auth/device-bootstrap   { device_id, device_label? }   →  { session_token, user }
POST /api/auth/claim              { device_id, email|phone, secret }
                                                                  →  { session_token, user, action }
POST /api/auth/login              { device_id, email|phone, secret, device_label? }
                                                                  →  { session_token, user }
POST /api/auth/me/identifiers     { current_secret, new_email?, new_phone?, new_secret? }   *bearer*
                                                                  →  user
POST /api/auth/recover            { email }                       →  { sent }
GET  /api/auth/me/identity        *bearer*                        →  user
```

Where `user` is `IdentityUserPayload` (section 2.6).

---

Ping the desktop owner if anything in section 2 disagrees with what the server actually returns — those endpoints went in fresh and haven't been exercised against a real client yet.
