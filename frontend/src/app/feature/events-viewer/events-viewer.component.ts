import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EMPTY, Subscription, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EventsService } from '../../api/services/events.service';
import { EventDto } from '../../api/models';

export const EVENT_TYPES: string[] = [
  'maintenance.request.created',
  'maintenance.request.assigned',
  'maintenance.inspection.submitted',
  'maintenance.decision.approved',
  'maintenance.decision.rejected',
  'maintenance.decision.info-requested',
  'maintenance.payment.ready',
];

@Component({
  selector: 'app-events-viewer',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './events-viewer.component.html',
  styleUrl: './events-viewer.component.scss',
})
export class EventsViewerComponent implements OnInit, OnDestroy {
  private readonly eventsService = inject(EventsService);

  readonly events = signal<EventDto[]>([]);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly currentPage = signal(0);
  readonly polling = signal(true);
  readonly expandedEventId = signal<string | null>(null);
  readonly eventTypeFilter = signal<string | undefined>(undefined);

  readonly displayedColumns: string[] = ['eventType', 'timestamp', 'eventId', 'expand'];
  readonly eventTypes = EVENT_TYPES;
  readonly isDetailRow = (_index: number, _row: EventDto): boolean => true;

  private pollSub: Subscription | undefined;
  private visibilityHandler: (() => void) | undefined;

  ngOnInit(): void {
    this.startPolling();

    this.visibilityHandler = () => {
      this.polling.set(document.visibilityState === 'visible');
    };
    document.addEventListener('visibilitychange', this.visibilityHandler);
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
    if (this.visibilityHandler) {
      document.removeEventListener('visibilitychange', this.visibilityHandler);
    }
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = timer(0, 5000)
      .pipe(
        switchMap(() =>
          this.eventsService
            .list({
              page: this.currentPage(),
              size: 20,
              eventType: this.eventTypeFilter(),
            })
            .pipe(catchError(() => EMPTY))
        )
      )
      .subscribe((page) => {
        this.events.set(page.content ?? []);
        this.totalElements.set(page.totalElements ?? 0);
        this.totalPages.set(page.totalPages ?? 0);
      });
  }

  toggleExpand(eventId: string | undefined): void {
    const id = eventId ?? null;
    this.expandedEventId.set(this.expandedEventId() === id ? null : id);
  }

  onPageChange(event: PageEvent): void {
    this.currentPage.set(event.pageIndex);
    this.startPolling();
  }

  onTypeChange(value: string): void {
    this.eventTypeFilter.set(value === '' ? undefined : value);
    this.currentPage.set(0);
    this.startPolling();
  }
}
