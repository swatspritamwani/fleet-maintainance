# Frontend Guardrails

> Referenced spec: `docs/functional-requirements.md` §8, §9.1
> These guardrails enforce Angular 17+ best practices and role-based access control for the fleet maintenance UI.

---

## GR-05 · `angular_standalone_components`

| | |
|---|---|
| **Severity** | 🟡 warning — logged to `ai-delivery-log.md`, does not halt delivery |
| **Enforcement** | ESLint custom rule + `review-checklist` after_codegen hook |

### Rule

All Angular components, directives, and pipes must be **standalone** (`standalone: true` in the decorator). No `NgModule` declarations anywhere in the application source code. Use Angular signals for local reactive state where applicable (§9.1).

### Rationale

Angular 17+ standalone components:
- Eliminate module boilerplate and reduce bundle size through better tree-shaking.
- Make dependency tracking explicit per component (each component declares its own `imports`).
- Are the modern Angular direction — NgModules are considered legacy.
- Enable `provideRouter`, `provideHttpClient`, and other functional providers without a root `AppModule`.

### Required Pattern

```typescript
// ✅ CORRECT: Standalone component with explicit imports
@Component({
  selector: 'app-create-request',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
  ],
  templateUrl: './create-request.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateRequestComponent {
  priority = signal<Priority | null>(null);
  form = this.fb.group({
    vehicleId:   ['', Validators.required],
    description: ['', [Validators.required, Validators.maxLength(2000)]],
    priority:    ['', Validators.required],
  });

  constructor(private fb: FormBuilder, private api: MaintenanceRequestsService) {}
}
```

### Prohibited Patterns

```typescript
// ❌ BANNED: NgModule declaration
@NgModule({
  declarations: [CreateRequestComponent],
  imports: [CommonModule],
})
export class RequestsModule {}  // VIOLATION GR-05

// ❌ BANNED: Component without standalone: true
@Component({
  selector: 'app-create-request',
  templateUrl: './create-request.component.html',
  // missing standalone: true — VIOLATION GR-05
})
export class CreateRequestComponent {}
```

### Application Bootstrap (No AppModule)

```typescript
// main.ts — bootstrap with standalone API
bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([correlationIdInterceptor, authInterceptor])),
    provideAnimationsAsync(),
    importProvidersFrom(MatSnackBarModule),
  ],
});
```

### Signals Usage Guidelines

Use signals for:
- Component-local state that drives template rendering (e.g., selected filters, loading flags, pagination).
- Replacing `BehaviorSubject` in services where the state is simple and doesn't need RxJS operators.

```typescript
// ✅ Preferred: signal for local state
export class RequestListComponent {
  requests     = signal<RequestSummary[]>([]);
  loading      = signal(false);
  statusFilter = signal<Status | null>(null);

  ngOnInit() {
    this.loadRequests();
  }

  loadRequests() {
    this.loading.set(true);
    this.api.listMaintenanceRequests({ status: this.statusFilter() })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe(page => this.requests.set(page.content));
  }
}
```

### How It Is Checked

1. **ESLint rule** — custom rule in `.eslintrc.json`:
```json
{
  "rules": {
    "no-ngmodule": "warn",
    "@angular-eslint/component-class-suffix": "warn"
  }
}
```
Custom plugin scans `@Component` decorators for missing `standalone: true`.

2. **`review-checklist` after_codegen hook**:
```bash
# Find any *.module.ts files in app source (excluding generated client code)
find frontend/src/app -name "*.module.ts" -not -path "*/api/generated/*"
# Any results = violation
```

3. **Grep scan**:
```bash
# Find components without standalone: true
grep -rn "@Component({" frontend/src/app/
# Verify each match has standalone: true on the next few lines
```

### Scope

This guardrail applies to all Angular code written in the `frontend/src/app/` directory. The generated OpenAPI client code in `frontend/src/app/api/generated/` is exempt if the generator does not produce standalone-compatible code — annotate the exemption in `ai-delivery-log.md`.
