import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InspectionService } from '../../api/services/inspection.service';
import { MaintenanceRequestService } from '../../api/services/maintenance-request.service';
import { ToastService } from '../../core/toast.service';
import { ProblemDetail, RequestDetailDto } from '../../api/models';

@Component({
  selector: 'app-submit-inspection',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './submit-inspection.component.html',
  styleUrl: './submit-inspection.component.scss',
})
export class SubmitInspectionComponent implements OnInit {
  private readonly inspectionService = inject(InspectionService);
  private readonly requestService = inject(MaintenanceRequestService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly id: string = this.route.snapshot.paramMap.get('id') ?? '';

  readonly request = signal<RequestDetailDto | null>(null);
  readonly submitting = signal(false);
  readonly attachments = signal<string[]>([]);

  readonly form = new FormGroup({
    findings: new FormControl('', {
      validators: [Validators.required, Validators.maxLength(5000)],
      nonNullable: true,
    }),
    estimatedCost: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    estimatedDurationDays: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(1)],
    }),
  });

  readonly attachmentInputCtrl = new FormControl('', { nonNullable: true });

  ngOnInit(): void {
    this.requestService.getById(this.id).subscribe({
      next: (req) => this.request.set(req),
      error: () => this.request.set(null),
    });
  }

  addAttachment(url: string): void {
    const trimmed = url.trim();
    if (trimmed.length === 0) {
      return;
    }
    this.attachments.update((list) => [...list, trimmed]);
    this.attachmentInputCtrl.reset();
  }

  removeAttachment(index: number): void {
    this.attachments.update((list) => list.filter((_, i) => i !== index));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { findings, estimatedCost, estimatedDurationDays } = this.form.getRawValue();
    this.submitting.set(true);
    this.inspectionService
      .submit(this.id, {
        findings,
        estimatedCost: estimatedCost!,
        estimatedDurationDays: estimatedDurationDays!,
        attachments: this.attachments(),
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          this.toastService.success('Inspection submitted');
          this.router.navigate(['/requests', this.id]);
        },
        error: (err: HttpErrorResponse) => {
          const problem = err.error as ProblemDetail;
          this.toastService.error(problem?.detail ?? 'Failed to submit inspection.');
        },
      });
  }

  cancel(): void {
    this.router.navigate(['/requests', this.id]);
  }
}
