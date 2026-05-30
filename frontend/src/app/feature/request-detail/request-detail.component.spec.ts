import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { RequestDetailComponent } from './request-detail.component';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { InspectionService } from '../../api/services/inspection.service';
import { DecisionService } from '../../api/services/decision.service';
import { RequestDetailDto, InspectionDto, DecisionDto } from '../../api/models';

const MOCK_REQUEST: RequestDetailDto = {
  requestId: 'test-id',
  vehicleId: 'VH-1',
  status: 'CREATED',
  priority: 'HIGH',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

const MOCK_INSPECTIONS: InspectionDto[] = [];
const MOCK_DECISIONS: DecisionDto[] = [];

describe('RequestDetailComponent', () => {
  let fixture: ComponentFixture<RequestDetailComponent>;
  let component: RequestDetailComponent;

  const mockDialogRef = {
    afterClosed: () => of('test remarks'),
  } as unknown as MatDialogRef<unknown, string>;

  const mockDialog = {
    open: jasmine.createSpy('open').and.returnValue(mockDialogRef),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, RequestDetailComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        provideRouter([{ path: 'requests/:id', component: RequestDetailComponent }]),
        { provide: MatDialog, useValue: mockDialog },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (_key: string) => 'test-id',
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RequestDetailComponent);
    component = fixture.componentInstance;
  });

  it('creates successfully', () => {
    spyOn(MaintenanceRequestService.prototype, 'getById').and.returnValue(of(MOCK_REQUEST));
    spyOn(InspectionService.prototype, 'list').and.returnValue(of(MOCK_INSPECTIONS));
    spyOn(DecisionService.prototype, 'list').and.returnValue(of(MOCK_DECISIONS));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('canAssign is false by default (no request loaded, no role set)', () => {
    spyOn(MaintenanceRequestService.prototype, 'getById').and.returnValue(of(MOCK_REQUEST));
    spyOn(InspectionService.prototype, 'list').and.returnValue(of(MOCK_INSPECTIONS));
    spyOn(DecisionService.prototype, 'list').and.returnValue(of(MOCK_DECISIONS));
    expect(component.canAssign()).toBeFalse();
  });

  it('loading starts false', () => {
    spyOn(MaintenanceRequestService.prototype, 'getById').and.returnValue(of(MOCK_REQUEST));
    spyOn(InspectionService.prototype, 'list').and.returnValue(of(MOCK_INSPECTIONS));
    spyOn(DecisionService.prototype, 'list').and.returnValue(of(MOCK_DECISIONS));
    expect(component.loading()).toBeFalse();
  });

  it('request signal is set after loadAll() resolves', () => {
    spyOn(MaintenanceRequestService.prototype, 'getById').and.returnValue(of(MOCK_REQUEST));
    spyOn(InspectionService.prototype, 'list').and.returnValue(of(MOCK_INSPECTIONS));
    spyOn(DecisionService.prototype, 'list').and.returnValue(of(MOCK_DECISIONS));
    component.loadAll();
    expect(component.request()).toEqual(MOCK_REQUEST);
    expect(component.inspections()).toEqual(MOCK_INSPECTIONS);
    expect(component.decisions()).toEqual(MOCK_DECISIONS);
  });
});
