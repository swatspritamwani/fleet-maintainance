import { Injectable } from '@angular/core';

const TOKEN_KEY = 'fm_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  clearToken(): void {
    localStorage.removeItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  getRole(): 'COORDINATOR' | 'SERVICE_PROVIDER' | null {
    const token = this.getToken();
    if (!token) {
      return null;
    }
    const payload = this.parsePayload(token);
    const roles = payload['roles'];
    if (!Array.isArray(roles) || roles.length === 0) {
      return null;
    }
    const first = roles[0];
    if (first === 'COORDINATOR' || first === 'SERVICE_PROVIDER') {
      return first;
    }
    return null;
  }

  getUserId(): string {
    const token = this.getToken();
    if (!token) {
      return 'anonymous';
    }
    const payload = this.parsePayload(token);
    const sub = payload['sub'];
    if (typeof sub === 'string' && sub.length > 0) {
      return sub;
    }
    return 'anonymous';
  }

  private parsePayload(token: string): Record<string, unknown> {
    try {
      const segment = token.split('.')[1];
      if (!segment) {
        return {};
      }
      const padded = segment.replace(/-/g, '+').replace(/_/g, '/');
      const json = atob(padded);
      return JSON.parse(json) as Record<string, unknown>;
    } catch {
      return {};
    }
  }
}
