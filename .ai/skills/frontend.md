# Frontend Skills

> Tech stack reference: `docs/functional-requirements.md` §9.1
> All frontend code must use these technologies only. All components must be standalone (GR-05).

---

## Angular 17+

| | |
|---|---|
| **Version** | 17+ (latest stable) |
| **Purpose** | SPA framework for all 7 UI screens defined in §8.1. |

### Screens and Routes

| Screen | Route | Actor | Key Features |
|--------|-------|-------|-------------|
| Request List | `/requests` | Coordinator | Table with status/priority/date filters, pagination |
| Create Request | `/requests/new` | Coordinator | Form: vehicleId dropdown, description textarea, priority select |
| Request Detail | `/requests/:id` | Both | Request info, inspection reports, decision history, action buttons |
| Assign Provider | `/requests/:id/assign` | Coordinator | Active provider dropdown, Assign button |
| Submit Inspection | `/requests/:id/inspect` | Service Provider | findings textarea, estimatedCost, estimatedDurationDays, file upload |
| Decision Panel | within `/requests/:id` | Coordinator | Approve / Reject / Request Info buttons; remarks modal |
| Kafka Events Viewer | `/events` | Coordinator | Polling-based event list: topic, timestamp, payload preview |

### Standalone Component Pattern

```typescript
// Every component must use standalone: true — no NgModules (GR-05)
@Component({
  selector: 'app-request-list',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTableModule, MatPaginatorModule],
  templateUrl: './request-list.component.html',
})
export class RequestListComponent {
  requests = signal<MaintenanceRequestSummary[]>([]);
}
```

### When to Use

All frontend UI development: components, services, route guards, pipes.

### When NOT to Use

- Do **not** declare any `NgModule` — no `AppModule`, `SharedModule`, or `FeatureModule` (GR-05).
- Do **not** use Angular Universal (SSR) — desktop-first SPA per §8.2.
- Do **not** import backend Java types — use generated TypeScript client DTOs only.

---

## TypeScript 5.x

| | |
|---|---|
| **Version** | 5.x (strict mode) |
| **Purpose** | Primary language for all Angular code. Strict type safety throughout. |

### Required `tsconfig.json` Settings

```json
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "target": "ES2022",
    "module": "ES2022"
  }
}
```

### When to Use

All `.ts` source files including components, services, guards, interceptors, and generated OpenAPI client.

### When NOT to Use

- Do **not** use `any` type in business logic — use proper interfaces or generated DTO types.
- Do **not** disable strict checks via `// @ts-ignore` except in generated code.
- Do **not** write raw HTTP calls with loosely typed responses — always type API responses against generated client interfaces.

---

## RxJS

| | |
|---|---|
| **Version** | Bundled with Angular 17 |
| **Purpose** | Reactive streams for HTTP responses, Kafka event polling, and error handling pipelines. |

### Patterns to Use

```typescript
// HTTP call with error handling
this.requestService.getRequests(filters).pipe(
  catchError(err => {
    this.toastService.error(err.error?.detail ?? 'Failed to load requests');
    return EMPTY;
  })
).subscribe(page => this.requests.set(page.content));

// Polling for Kafka events (§8.1 Kafka Events Viewer)
this.eventPolling$ = timer(0, 5000).pipe(
  switchMap(() => this.eventService.getRecentEvents()),
  distinctUntilChanged((a, b) => a.length === b.length)
);
```

### When to Use

- Angular services making HTTP calls via the generated OpenAPI client.
- Polling-based Kafka event feed on the `/events` screen.
- Error handling pipelines (`catchError`, `retry`).
- Combining multiple streams (e.g., loading request detail + its inspections concurrently with `forkJoin`).

### When NOT to Use

- Simple one-shot reads — prefer Angular's `toSignal()` wrapper over raw `Observable` subscriptions where the lifecycle is component-scoped.
- Do **not** use `BehaviorSubject` for global state shared across many components — prefer Angular signals or a lightweight state service.
- Do **not** nest subscriptions — use `switchMap`, `mergeMap`, or `concatMap` instead.

---

## Angular Material / PrimeNG

| | |
|---|---|
| **Version** | Latest compatible with Angular 17 |
| **Purpose** | UI component library for tables, forms, modals, and toast notifications (§8.1, §8.2). |

### Components to Use

| UI Need | Angular Material | PrimeNG Equivalent |
|---------|-----------------|-------------------|
| Data table with sort/filter | `MatTable`, `MatSort`, `MatPaginator` | `p-table` |
| Form inputs | `MatFormField`, `MatSelect`, `MatInput` | `p-dropdown`, `p-inputText` |
| Remarks modal (Reject/Request Info) | `MatDialog` | `p-dialog` |
| Toast notifications | `MatSnackBar` | `p-toast` |
| File upload (inspection attachments) | custom + `MatButton` | `p-fileUpload` |
| Progress/loading indicator | `MatProgressSpinner` | `p-progressSpinner` |
| Buttons | `MatButton`, `MatIconButton` | `p-button` |

### When to Use

All UI components across the 7 screens in §8.1.

### When NOT to Use

- **Do not mix Angular Material and PrimeNG in the same project** — choose one library at project start and apply it consistently across all components.
- Do not build custom table, modal, or toast components from scratch when the chosen library provides them.

---

## Angular Router

| | |
|---|---|
| **Version** | Bundled with Angular 17 |
| **Purpose** | Client-side routing and role-based route guards (Coordinator vs Service Provider). |

### Route Configuration

```typescript
export const routes: Routes = [
  {
    path: 'requests',
    canActivate: [authGuard],
    children: [
      { path: '', component: RequestListComponent },
      { path: 'new', component: CreateRequestComponent, canActivate: [coordinatorGuard] },
      { path: ':id', component: RequestDetailComponent },
      { path: ':id/assign', component: AssignProviderComponent, canActivate: [coordinatorGuard] },
      { path: ':id/inspect', component: SubmitInspectionComponent, canActivate: [providerGuard] },
    ]
  },
  { path: 'events', component: KafkaEventsViewerComponent, canActivate: [coordinatorGuard] },
  { path: '**', redirectTo: 'requests' }
];
```

### Route Guards

| Guard | Protects | Logic |
|-------|----------|-------|
| `authGuard` | All routes | Redirect to IdP login if no valid token |
| `coordinatorGuard` | `/requests/new`, `/:id/assign`, `/events` | Allow if role = `COORDINATOR`; else 403 page |
| `providerGuard` | `/:id/inspect` | Allow if role = `SERVICE_PROVIDER`; else 403 page |

### When to Use

All route definitions. Every protected route must have the appropriate `canActivate` guard (NFR-3).

### When NOT to Use

- Do **not** rely solely on UI hiding (e.g., `*ngIf` on buttons) for role enforcement — always use route guards as the authoritative check.
- Do **not** use `CanLoad` for lazy-loaded modules — use `canMatch` with standalone components (Angular 17 pattern).
