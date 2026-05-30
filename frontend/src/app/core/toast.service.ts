import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, undefined, {
      duration: 3000,
      panelClass: ['snack-success'],
    });
  }

  error(message: string): void {
    this.snackBar.open(message, undefined, {
      duration: 5000,
      panelClass: ['snack-error'],
    });
  }
}
