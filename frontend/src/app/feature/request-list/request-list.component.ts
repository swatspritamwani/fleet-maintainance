import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { ToastService } from '../../core/toast.service';
import { StatusBadgePipe } from '../../shared/pipes/status-badge.pipe';
import { Priority, RequestDto, RequestStatus } from '../../api/models';

@Component({
  selector: 'app-request-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
    StatusBadgePipe,
  ],
  templateUrl: './request-list.component.html',
  styleUrl: './request-list.component.scss',
})
export class RequestListComponent implements OnInit {
  private readonly requestService = inject(MaintenanceRequestService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  readonly requests = signal<RequestDto[]>([]);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly currentPage = signal(0);
  readonly filterStatus = signal<RequestStatus | undefined>(undefined);
  readonly filterPriority = signal<Priority | undefined>(undefined);

  readonly displayedColumns = ['requestId', 'vehicleId', 'priority', 'status', 'createdAt', 'actions'];

  readonly statusOptions: RequestStatus[] = [
    'CREATED',
    'ASSIGNED',
    'INSPECTION_SUBMITTED',
    'APPROVED',
    'REJECTED',
    'INFO_REQUESTED',
    'PAYMENT_READY',
  ];

  readonly priorityOptions: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  ngOnInit(): void {
    this.loadRequests();
  }

  loadRequests(): void {
    this.loading.set(true);
    this.requestService
      .list({
        status: this.filterStatus(),
        priority: this.filterPriority(),
        page: this.currentPage(),
        size: 20,
        sort: 'createdAt,desc',
      })
      .subscribe({
        next: (page) => {
          this.requests.set(page.content ?? []);
          this.totalElements.set(page.totalElements ?? 0);
          this.totalPages.set(page.totalPages ?? 0);
          this.loading.set(false);
        },
        error: () => {
          this.toastService.error('Failed to load maintenance requests.');
          this.loading.set(false);
        },
      });
  }

  onRowClick(id?: string): void {
    if (id) {
      this.router.navigate(['/requests', id]);
    }
  }

  onStatusChange(value: RequestStatus | ''): void {
    this.filterStatus.set(value === '' ? undefined : value);
    this.currentPage.set(0);
    this.loadRequests();
  }

  onPriorityChange(value: Priority | ''): void {
    this.filterPriority.set(value === '' ? undefined : value);
    this.currentPage.set(0);
    this.loadRequests();
  }

  onPageChange(event: PageEvent): void {
    this.currentPage.set(event.pageIndex);
    this.loadRequests();
  }

  newRequest(): void {
    this.router.navigate(['/requests/new']);
  }
}
