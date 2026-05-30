import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { InspectionService } from '../../api/services/inspection.service';
import { DecisionService } from '../../api/services/decision.service';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { StatusBadgePipe } from '../../shared/pipes/status-badge.pipe';
import {
  DecisionDto,
  DecisionOutcome,
  InspectionDto,
  RequestDetailDto,
} from '../../api/models';
import {
  RemarksDialogComponent,
  RemarksDialogData,
} from './remarks-dialog/remarks-dialog.component';

@Component({
  selector: 'app-request-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatListModule,
    MatProgressSpinnerModule,
    StatusBadgePipe,
    RemarksDialogComponent,
  ],
  templateUrl: './request-detail.component.html',
  styleUrl: './request-detail.component.scss',
})
export class RequestDetailComponent implements OnInit {
  private readonly requestService = inject(MaintenanceRequestService);
  private readonly inspectionService = inject(InspectionService);
  private readonly decisionService = inject(DecisionService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly id: string = this.route.snapshot.paramMap.get('id') ?? '';

  readonly request = signal<RequestDetailDto | null>(null);
  readonly inspections = signal<InspectionDto[]>([]);
  readonly decisions = signal<DecisionDto[]>([]);
  readonly loading = signal(false);

  private readonly role = signal(this.authService.getRole());

  readonly canAssign = computed(
    () => this.request()?.status === 'CREATED' && this.role() === 'COORDINATOR'
  );

  readonly canDecide = computed(
    () =>
      this.request()?.status === 'INSPECTION_SUBMITTED' && this.role() === 'COORDINATOR'
  );

  readonly canInspect = computed(
    () =>
      (this.request()?.status === 'ASSIGNED' ||
        this.request()?.status === 'INFO_REQUESTED') &&
      this.role() === 'SERVICE_PROVIDER'
  );

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    if (!this.id) {
      return;
    }
    this.loading.set(true);
    forkJoin([
      this.requestService.getById(this.id),
      this.inspectionService.list(this.id),
      this.decisionService.list(this.id),
    ]).subscribe({
      next: ([req, insp, dec]) => {
        this.request.set(req);
        this.inspections.set(insp);
        this.decisions.set(dec);
        this.loading.set(false);
      },
      error: () => {
        this.toastService.error('Failed to load request details.');
        this.loading.set(false);
      },
    });
  }

  approve(): void {
    this.decisionService.submit(this.id, { outcome: 'APPROVED' }).subscribe({
      next: () => {
        this.toastService.success('Request approved successfully.');
        this.loadAll();
      },
      error: () => this.toastService.error('Failed to submit approval.'),
    });
  }

  openRemarksDialog(outcome: Extract<DecisionOutcome, 'REJECTED' | 'INFO_REQUESTED'>): void {
    const ref = this.dialog.open<RemarksDialogComponent, RemarksDialogData, string>(
      RemarksDialogComponent,
      {
        width: '480px',
        data: { outcome },
      }
    );
    ref.afterClosed().subscribe((remarks) => {
      if (remarks === undefined || remarks === null) {
        return;
      }
      this.decisionService.submit(this.id, { outcome, remarks }).subscribe({
        next: () => {
          this.toastService.success(
            outcome === 'REJECTED' ? 'Request rejected.' : 'More information requested.'
          );
          this.loadAll();
        },
        error: () => this.toastService.error('Failed to submit decision.'),
      });
    });
  }

  navigateAssign(): void {
    this.router.navigate(['/requests', this.id, 'assign']);
  }

  navigateInspect(): void {
    this.router.navigate(['/requests', this.id, 'inspect']);
  }

  goBack(): void {
    this.router.navigate(['/requests']);
  }
}
