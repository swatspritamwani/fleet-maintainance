import { StatusBadgePipe } from './status-badge.pipe';
import { RequestStatus } from '../../api/models';

describe('StatusBadgePipe', () => {
  let pipe: StatusBadgePipe;

  beforeEach(() => {
    pipe = new StatusBadgePipe();
  });

  it('maps CREATED to badge-created', () => {
    expect(pipe.transform('CREATED')).toBe('badge-created');
  });

  it('maps ASSIGNED to badge-assigned', () => {
    expect(pipe.transform('ASSIGNED')).toBe('badge-assigned');
  });

  it('maps INSPECTION_SUBMITTED to badge-inspection', () => {
    expect(pipe.transform('INSPECTION_SUBMITTED')).toBe('badge-inspection');
  });

  it('maps APPROVED to badge-approved', () => {
    expect(pipe.transform('APPROVED')).toBe('badge-approved');
  });

  it('maps REJECTED to badge-rejected', () => {
    expect(pipe.transform('REJECTED')).toBe('badge-rejected');
  });

  it('maps INFO_REQUESTED to badge-info', () => {
    expect(pipe.transform('INFO_REQUESTED')).toBe('badge-info');
  });

  it('maps PAYMENT_READY to badge-payment', () => {
    expect(pipe.transform('PAYMENT_READY')).toBe('badge-payment');
  });

  it('returns badge-default for an unknown value', () => {
    expect(pipe.transform('UNKNOWN_VALUE' as RequestStatus)).toBe('badge-default');
  });
});
