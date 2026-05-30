import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DecisionOutcome } from '../../../api/models';

export interface RemarksDialogData {
  outcome: Extract<DecisionOutcome, 'REJECTED' | 'INFO_REQUESTED'>;
}

@Component({
  selector: 'app-remarks-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  templateUrl: './remarks-dialog.component.html',
})
export class RemarksDialogComponent {
  readonly dialogRef = inject<MatDialogRef<RemarksDialogComponent, string>>(MatDialogRef);
  readonly data = inject<RemarksDialogData>(MAT_DIALOG_DATA);

  readonly remarks = new FormControl('', {
    validators: [Validators.required, Validators.maxLength(2000)],
    nonNullable: true,
  });

  get title(): string {
    return this.data.outcome === 'REJECTED' ? 'Reject Request' : 'Request More Information';
  }

  confirm(): void {
    if (this.remarks.invalid) {
      this.remarks.markAsTouched();
      return;
    }
    this.dialogRef.close(this.remarks.value);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
