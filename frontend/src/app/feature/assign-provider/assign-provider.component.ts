import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { ServiceProviderService } from '../../api/services/service-provider.service';
import { ToastService } from '../../core/toast.service';
import { ProblemDetail, ProviderDto, RequestDetailDto } from '../../api/models';
import { StatusBadgePipe } from '../../shared/pipes/status-badge.pipe';

@Component({
  selector: 'app-assign-provider',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    StatusBadgePipe,
  ],
  templateUrl: './assign-provider.component.html',
  styleUrl: './assign-provider.component.scss',
})
export class AssignProviderComponent implements OnInit {
  private readonly requestService = inject(MaintenanceRequestService);
  private readonly providerService = inject(ServiceProviderService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly id: string = this.route.snapshot.paramMap.get('id') ?? '';

  readonly providers = signal<ProviderDto[]>([]);
  readonly request = signal<RequestDetailDto | null>(null);
  readonly submitting = signal(false);

  readonly providerCtrl = new FormControl<string>('', {
    validators: [Validators.required],
    nonNullable: true,
  });

  ngOnInit(): void {
    this.providerService.listActive().subscribe({
      next: (list) => this.providers.set(list),
      error: () => this.providers.set([]),
    });
    this.requestService.getById(this.id).subscribe({
      next: (req) => this.request.set(req),
      error: () => this.request.set(null),
    });
  }

  submit(): void {
    if (this.providerCtrl.invalid) {
      this.providerCtrl.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.requestService
      .assignProvider(this.id, { providerId: this.providerCtrl.value })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toastService.success('Provider assigned');
          this.router.navigate(['/requests', this.id]);
        },
        error: (err: HttpErrorResponse) => {
          const problem = err.error as ProblemDetail;
          this.toastService.error(problem?.detail ?? 'Failed to assign provider.');
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/requests', this.id]);
  }
}
