import { Routes } from '@angular/router';
import { authGuard, coordinatorGuard, providerGuard } from './core/guards';

export const routes: Routes = [
  {
    path: 'requests',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./feature/request-list/request-list.component').then(
            (m) => m.RequestListComponent
          ),
      },
      {
        path: 'new',
        canActivate: [coordinatorGuard],
        loadComponent: () =>
          import('./feature/create-request/create-request.component').then(
            (m) => m.CreateRequestComponent
          ),
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./feature/request-detail/request-detail.component').then(
            (m) => m.RequestDetailComponent
          ),
      },
      {
        path: ':id/assign',
        canActivate: [coordinatorGuard],
        loadComponent: () =>
          import('./feature/assign-provider/assign-provider.component').then(
            (m) => m.AssignProviderComponent
          ),
      },
      {
        path: ':id/inspect',
        canActivate: [providerGuard],
        loadComponent: () =>
          import('./feature/submit-inspection/submit-inspection.component').then(
            (m) => m.SubmitInspectionComponent
          ),
      },
    ],
  },
  {
    path: 'events',
    canActivate: [coordinatorGuard],
    loadComponent: () =>
      import('./feature/events-viewer/events-viewer.component').then(
        (m) => m.EventsViewerComponent
      ),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./feature/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'unauthorized',
    loadComponent: () =>
      import('./feature/login/login.component').then((m) => m.LoginComponent),
  },
  { path: '', redirectTo: 'requests', pathMatch: 'full' },
  { path: '**', redirectTo: 'requests' },
];
