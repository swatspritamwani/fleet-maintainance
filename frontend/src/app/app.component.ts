import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet, Router } from '@angular/router';
import { NgIf } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule, MatIconModule, NgIf],
  template: `
    <mat-toolbar color="primary">
      <a mat-button routerLink="/requests">
        <mat-icon>build</mat-icon>
        Fleet Maintenance
      </a>
      <span class="spacer"></span>
      <a mat-button routerLink="/requests">Requests</a>
      <a mat-button routerLink="/events" *ngIf="isCoordinator()">Events</a>
      <span class="role-badge" *ngIf="role()">{{ role() }}</span>
      <button mat-button (click)="logout()">
        <mat-icon>logout</mat-icon>
        Logout
      </button>
    </mat-toolbar>
    <main class="app-content">
      <router-outlet />
    </main>
  `,
  styles: [
    `
      mat-toolbar { position: sticky; top: 0; z-index: 100; }
      .spacer { flex: 1 1 auto; }
      .role-badge {
        font-size: 0.75rem;
        background: rgba(255, 255, 255, 0.2);
        border-radius: 4px;
        padding: 2px 8px;
        margin-right: 8px;
      }
      .app-content { padding: 24px; }
    `,
  ],
})
export class AppComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = computed(() => this.authService.getRole());
  readonly isCoordinator = computed(() => this.authService.getRole() === 'COORDINATOR');

  logout(): void {
    this.authService.clearToken();
    this.router.navigate(['/login']);
  }
}
