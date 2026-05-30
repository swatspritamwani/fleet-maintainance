import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

const COORDINATOR_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkZW1vLXVzZXIiLCJyb2xlcyI6WyJDT09SRElOQVRPUiJdfQ.demo';

const SERVICE_PROVIDER_TOKEN =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.' +
  btoa(JSON.stringify({ sub: 'sp-user', roles: ['SERVICE_PROVIDER'] })) +
  '.demo';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(AuthService);
  });

  it('isAuthenticated() returns false when localStorage is empty', () => {
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('setToken + isAuthenticated() returns true', () => {
    service.setToken(COORDINATOR_TOKEN);
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('getRole() returns COORDINATOR when token has roles:["COORDINATOR"]', () => {
    service.setToken(COORDINATOR_TOKEN);
    expect(service.getRole()).toBe('COORDINATOR');
  });

  it('getRole() returns SERVICE_PROVIDER when token has roles:["SERVICE_PROVIDER"]', () => {
    service.setToken(SERVICE_PROVIDER_TOKEN);
    expect(service.getRole()).toBe('SERVICE_PROVIDER');
  });

  it('getRole() returns null when no token', () => {
    expect(service.getRole()).toBeNull();
  });

  it('getUserId() returns demo-user for the coordinator token', () => {
    service.setToken(COORDINATOR_TOKEN);
    expect(service.getUserId()).toBe('demo-user');
  });

  it('clearToken() removes the token and isAuthenticated() returns false', () => {
    service.setToken(COORDINATOR_TOKEN);
    expect(service.isAuthenticated()).toBeTrue();
    service.clearToken();
    expect(service.isAuthenticated()).toBeFalse();
  });
});
