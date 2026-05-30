import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { RequestListComponent } from './request-list.component';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { PagedRequestDto } from '../../api/models';

const MOCK_PAGE: PagedRequestDto = {
  content: [
    {
      requestId: 'r1',
      vehicleId: 'VH-1',
      status: 'CREATED',
      priority: 'HIGH',
      createdAt: '2026-01-01T00:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  page: 0,
  size: 20,
};

describe('RequestListComponent', () => {
  let fixture: ComponentFixture<RequestListComponent>;
  let component: RequestListComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, RequestListComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RequestListComponent);
    component = fixture.componentInstance;
  });

  it('creates successfully', () => {
    spyOn(MaintenanceRequestService.prototype, 'list').and.returnValue(of(MOCK_PAGE));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('loading signal is false by default (before detectChanges)', () => {
    spyOn(MaintenanceRequestService.prototype, 'list').and.returnValue(of(MOCK_PAGE));
    expect(component.loading()).toBeFalse();
  });

  it('requests signal is empty by default (before detectChanges)', () => {
    spyOn(MaintenanceRequestService.prototype, 'list').and.returnValue(of(MOCK_PAGE));
    expect(component.requests()).toEqual([]);
  });

  it('requests signal is populated after loadRequests() resolves', () => {
    spyOn(MaintenanceRequestService.prototype, 'list').and.returnValue(of(MOCK_PAGE));
    component.loadRequests();
    expect(component.requests().length).toBe(1);
    expect(component.requests()[0].requestId).toBe('r1');
  });

  it('onRowClick navigates to /requests/r1', () => {
    spyOn(MaintenanceRequestService.prototype, 'list').and.returnValue(of(MOCK_PAGE));
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate');
    component.onRowClick('r1');
    expect(navigateSpy).toHaveBeenCalledWith(['/requests', 'r1']);
  });
});
