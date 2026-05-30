export type RequestStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'INSPECTION_SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'INFO_REQUESTED'
  | 'PAYMENT_READY';

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type DecisionOutcome = 'APPROVED' | 'REJECTED' | 'INFO_REQUESTED';

export interface CreateRequestDto {
  vehicleId: string;
  description: string;
  priority: Priority;
}

export interface AssignProviderDto {
  providerId: string;
}

export interface InspectionReportDto {
  findings: string;
  estimatedCost: number;
  estimatedDurationDays: number;
  attachments?: string[];
}

export interface DecisionRequestDto {
  outcome: DecisionOutcome;
  remarks?: string;
}

export interface Money {
  amount?: number;
  currency?: string;
}

export interface VehicleRef {
  vehicleId?: string;
  licensePlate?: string;
  make?: string;
  model?: string;
}

export interface RequestDto {
  requestId?: string;
  vehicleId?: string;
  description?: string;
  priority?: Priority;
  status?: RequestStatus;
  assignedProviderId?: string | null;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProviderDto {
  providerId?: string;
  name?: string;
  contactEmail?: string;
  phone?: string;
  active?: boolean;
}

export interface InspectionDto {
  reportId?: string;
  requestId?: string;
  findings?: string;
  estimatedCost?: Money;
  estimatedDurationDays?: number;
  attachments?: string[];
  submittedBy?: string;
  submittedAt?: string;
}

export interface DecisionDto {
  decisionId?: string;
  requestId?: string;
  outcome?: DecisionOutcome;
  remarks?: string | null;
  decidedBy?: string;
  decidedAt?: string;
}

export interface RequestDetailDto {
  requestId?: string;
  vehicleId?: string;
  vehicle?: VehicleRef;
  description?: string;
  priority?: Priority;
  status?: RequestStatus;
  assignedProviderId?: string | null;
  assignedProvider?: ProviderDto;
  inspections?: InspectionDto[];
  decisions?: DecisionDto[];
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface EventDto {
  eventId?: string;
  eventType?: string;
  timestamp?: string;
  correlationId?: string;
  payload?: Record<string, unknown>;
}

export interface PagedRequestDto {
  content?: RequestDto[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PagedEventDto {
  content?: EventDto[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface FieldViolation {
  field?: string;
  message?: string;
  rejectedValue?: string | null;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  violations?: FieldViolation[];
}
