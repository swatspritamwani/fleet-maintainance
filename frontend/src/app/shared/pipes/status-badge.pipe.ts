import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'statusBadge',
  standalone: true,
  pure: true,
})
export class StatusBadgePipe implements PipeTransform {
  transform(status: string | undefined | null): string {
    switch (status) {
      case 'CREATED':
        return 'badge-created';
      case 'ASSIGNED':
        return 'badge-assigned';
      case 'INSPECTION_SUBMITTED':
        return 'badge-inspection';
      case 'APPROVED':
        return 'badge-approved';
      case 'REJECTED':
        return 'badge-rejected';
      case 'INFO_REQUESTED':
        return 'badge-info';
      case 'PAYMENT_READY':
        return 'badge-payment';
      default:
        return 'badge-default';
    }
  }
}
