# Story 3.13: Refresh Token Rotation & Silent Renewal

Status: backlog
Priority: medium
Type: tech-debt / security

## Story

As a user, I want my session to remain active for days without re-logging in, so that I don't lose my work context. As a security engineer, I want short-lived access tokens with refresh token rotation, so that stolen tokens have limited blast radius.

## Problem

Currently, the JWT access token is the only auth mechanism. It was initially set to 1 hour, causing users to be logged out mid-work. As a stopgap (Story 3.7), we bumped it to 24 hours across all environments. This is a UX improvement but a security trade-off — a stolen 24h JWT gives 24 hours of access with no way to revoke it.

## Proposed Solution

Implement refresh token rotation (OAuth2 best practice):

1. **Short-lived access token** (15 minutes) — JWT in HttpOnly cookie, same as today
2. **Long-lived refresh token** (30 days) — opaque token in a second HttpOnly cookie, stored hashed in DB
3. **Silent renewal** — frontend interceptor detects 401 on API calls, calls `/auth/refresh` endpoint, gets new access token, retries the original request transparently
4. **Token rotation** — each refresh issues a new refresh token and invalidates the old one (detects token reuse = compromise)
5. **Revocation** — `/auth/logout` invalidates the refresh token in DB; admin can revoke all sessions for a user

## Acceptance Criteria

- [ ] AC1: Access token expires after 15 minutes; refresh token after 30 days
- [ ] AC2: Frontend silently refreshes on 401 without user interaction or page reload
- [ ] AC3: Refresh token rotation — each refresh invalidates the previous token
- [ ] AC4: Token reuse detection — if an old refresh token is used, ALL sessions for that user are revoked
- [ ] AC5: Logout invalidates the refresh token server-side
- [ ] AC6: Existing 401 interceptor (from Story 3.7) becomes the fallback — if silent refresh also fails, THEN redirect to login

## Key Files to Create or Modify

| File | Action | Notes |
|---|---|---|
| `refresh_tokens` DB table | **Create** | Flyway migration: id, user_id, token_hash, expires_at, revoked_at, created_at |
| `RefreshTokenService.java` | **Create** | Issue, validate, rotate, revoke refresh tokens |
| `AuthController.java` | **Modify** | Add `/auth/refresh` endpoint |
| `OAuth2AuthenticationSuccessHandler.java` | **Modify** | Issue refresh token on login |
| `SecurityConfig.java` | **Modify** | Permit `/auth/refresh` without access token |
| `api-locale.ts` plugin | **Modify** | Add silent refresh retry before redirecting to login on 401 |
| `application.yml` | **Modify** | access-token-expiration-ms: 900000, refresh-token-expiration-days: 30 |

## References

- [OWAF Token Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- Story 3.7 — 401 interceptor in `api-locale.ts` (foundation for silent refresh retry)
- Current auth flow: `OAuth2AuthenticationSuccessHandler` → `TokenProvider` → HttpOnly cookie
