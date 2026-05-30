import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PagedEventDto } from '../models';

@Injectable({ providedIn: 'root' })
export class EventsService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/events';

  list(params?: {
    eventType?: string;
    since?: string;
    page?: number;
    size?: number;
  }): Observable<PagedEventDto> {
    let httpParams = new HttpParams();
    if (params?.eventType !== undefined) {
      httpParams = httpParams.set('eventType', params.eventType);
    }
    if (params?.since !== undefined) {
      httpParams = httpParams.set('since', params.since);
    }
    if (params?.page !== undefined) {
      httpParams = httpParams.set('page', params.page);
    }
    if (params?.size !== undefined) {
      httpParams = httpParams.set('size', params.size);
    }
    return this.http.get<PagedEventDto>(this.base, { params: httpParams });
  }
}
