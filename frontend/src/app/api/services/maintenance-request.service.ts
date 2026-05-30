import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AssignProviderDto,
  CreateRequestDto,
  PagedRequestDto,
  Priority,
  RequestDetailDto,
  RequestDto,
  RequestStatus,
} from '../models';

@Injectable({ providedIn: 'root' })
export class MaintenanceRequestService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/maintenance-requests';

  list(params?: {
    status?: RequestStatus;
    priority?: Priority;
    page?: number;
    size?: number;
    sort?: string;
  }): Observable<PagedRequestDto> {
    let httpParams = new HttpParams();
    if (params?.status !== undefined) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params?.priority !== undefined) {
      httpParams = httpParams.set('priority', params.priority);
    }
    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params?.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }
    if (params?.sort !== undefined) {
      httpParams = httpParams.set('sort', params.sort);
    }
    return this.http.get<PagedRequestDto>(this.base, { params: httpParams });
  }

  getById(id: string): Observable<RequestDetailDto> {
    return this.http.get<RequestDetailDto>(`${this.base}/${id}`);
  }

  create(dto: CreateRequestDto): Observable<RequestDto> {
    return this.http.post<RequestDto>(this.base, dto);
  }

  assignProvider(id: string, dto: AssignProviderDto): Observable<RequestDto> {
    return this.http.post<RequestDto>(`${this.base}/${id}/assignments`, dto);
  }
}
