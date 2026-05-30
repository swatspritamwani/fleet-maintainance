import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { EventsViewerComponent } from './events-viewer.component';
import { EventsService } from '../../api/services/events.service';
import { ToastService } from '../../core/toast.service';
import { PagedEventDto } from '../../api/models';

const EMPTY_PAGE: PagedEventDto = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  page: 0,
  size: 20,
};

describe('EventsViewerComponent', () => {
  let fixture: ComponentFixture<EventsViewerComponent>;
  let component: EventsViewerComponent;
  let eventsServiceSpy: jasmine.SpyObj<EventsService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    eventsServiceSpy = jasmine.createSpyObj<EventsService>('EventsService', ['list']);
    eventsServiceSpy.list.and.returnValue(of(EMPTY_PAGE));

    toastServiceSpy = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, EventsViewerComponent],
      providers: [
        { provide: EventsService, useValue: eventsServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventsViewerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('creates successfully', () => {
    expect(component).toBeTruthy();
  });

  it('events signal starts empty', () => {
    expect(component.events()).toEqual([]);
  });

  it('polling signal starts true', () => {
    expect(component.polling()).toBeTrue();
  });

  it('toggleExpand sets expandedEventId to the given id', () => {
    component.toggleExpand('event-1');
    expect(component.expandedEventId()).toBe('event-1');
  });

  it('calling toggleExpand with the same id again sets expandedEventId to null', () => {
    component.toggleExpand('event-1');
    expect(component.expandedEventId()).toBe('event-1');
    component.toggleExpand('event-1');
    expect(component.expandedEventId()).toBeNull();
  });

  it('onTypeChange with empty string sets eventTypeFilter to undefined', () => {
    component.onTypeChange('');
    expect(component.eventTypeFilter()).toBeUndefined();
  });

  it('onTypeChange with a type string sets eventTypeFilter to that value', () => {
    component.onTypeChange('maintenance.request.created');
    expect(component.eventTypeFilter()).toBe('maintenance.request.created');
  });
});
