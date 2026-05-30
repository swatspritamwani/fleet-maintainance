import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../core/auth.service';

const COORDINATOR_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
  '.eyJzdWIiOiJkZW1vLXVzZXIiLCJyb2xlcyI6WyJDT09SRElOQVRPUiJdfQ' +
  '.demo';

const PROVIDER_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9' +
  '.eyJzdWIiOiJkZW1vLXVzZXIiLCJyb2xlcyI6WyJTRVJWSUNFX1BST1ZJREVSIl19' +
  '.demo';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [MatButtonModule, MatCardModule],
  template: `
    <div class="login-container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Fleet Maintenance — Demo Login</mat-card-title>
          <mat-card-subtitle>Select a role to continue</mat-card-subtitle>
        </mat-card-header>
        <mat-card-actions align="end">
          <button mat-raised-button color="primary" (click)="loginAs(coordinatorToken)">
            Login as Coordinator
          </button>
          <button mat-raised-button color="accent" (click)="loginAs(providerToken)">
            Login as Service Provider
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [
    `
      .login-container {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 60vh;
      }
      mat-card {
        min-width: 360px;
        padding: 16px;
      }
      mat-card-actions button {
        margin-left: 8px;
      }
    `,
  ],
})
export class LoginComponent {
  readonly coordinatorToken = COORDINATOR_TOKEN;
  readonly providerToken = PROVIDER_TOKEN;

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  loginAs(token: string): void {
    this.authService.setToken(token);
    this.router.navigate(['/requests']);
  }
}
