import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { ServiceProviderService } from '../../api/services/service-provider.service';
import { ToastService } from '../../core/toast.service';
import { Priority, ProblemDetail, ProviderDto } from '../../api/models';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-create-request',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatCardModule,
  ],
  templateUrl: './create-request.component.html',
  styleUrl: './create-request.component.scss',
})
export class CreateRequestComponent implements OnInit {
  private readonly requestService = inject(MaintenanceRequestService);
  private readonly providerService = inject(ServiceProviderService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly providers = signal<ProviderDto[]>([]);

  readonly priorityOptions: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  readonly form = new FormGroup({
    vehicleId: new FormControl('', {
      validators: [Validators.required],
      nonNullable: true,
    }),
    description: new FormControl('', {
      validators: [Validators.required, Validators.maxLength(2000)],
      nonNullable: true,
    }),
    priority: new FormControl<Priority | ''>('', {
      validators: [Validators.required],
      nonNullable: true,
    }),
  });

  ngOnInit(): void {
    this.providerService.listActive().subscribe({
      next: (list) => this.providers.set(list),
      error: () => this.providers.set([]),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { vehicleId, description, priority } = this.form.getRawValue();
    this.submitting.set(true);
    this.requestService
      .create({ vehicleId, description, priority: priority as Priority })
      .subscribe({
        next: (result) => {
          this.submitting.set(false);
          this.toastService.success('Request created successfully.');
          this.router.navigate(['/requests', result.requestId]);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          const problem = err.error as ProblemDetail;
          const message = problem?.detail ?? 'Failed to create request. Please try again.';
          this.toastService.error(message);
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/requests']);
  }
}
