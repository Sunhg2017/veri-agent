# portal-web

React + TypeScript + Vite frontend for the WP1 management console.

## Current Scope

- Enterprise console layout with sidebar, top bar, and working area.
- SuperAdmin bootstrap page calling `POST /api/v1/bootstrap/super-admin`.
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

Start the backend with a bootstrap token:

```bash
WP1_BOOTSTRAP_TOKEN=local-init-token mvn -pl platform-api spring-boot:run
```

Then open the frontend and initialize the first SuperAdmin with the same token.

## Build

```bash
npm run build
```

