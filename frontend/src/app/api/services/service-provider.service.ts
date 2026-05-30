import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProviderDto } from '../models';

@Injectable({ providedIn: 'root' })
export class ServiceProviderService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/service-providers';

  listActive(): Observable<ProviderDto[]> {
    return this.http.get<ProviderDto[]>(this.base);
  }
}
