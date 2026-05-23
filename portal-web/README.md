# portal-web

React + TypeScript + Vite frontend for the WP1 management console.

Repository-level collaboration and delivery rules are defined in `../AGENTS.md`. Frontend changes must include the UI impact review and validation required there.

## Current Scope

- Enterprise console layout with sidebar, top bar, and working area.
- Login, logout, current-user, and forced first-login password change flows.
- Backend health widget calling `GET /api/v1/health`.
- Unified API response handling with `trace_id` display.
- Basic form validation, loading state, and error handling.

## Run

```bash
npm install
npm run dev
```

The Vite dev server listens on `http://localhost:5173`.

## Backend Proxy

During development, `/api/**` is proxied to `http://localhost:8080`.

Start the backend with an auth token secret:

```bash
WP1_AUTH_TOKEN_SECRET=local-auth-secret mvn -pl platform-api spring-boot:run
```

For the `db` profile, initialize SuperAdmin with `scripts/wp1_seed_super_admin.sh` before logging in.

## Build

```bash
npm run build
```
