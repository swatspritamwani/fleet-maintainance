import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DecisionDto, DecisionRequestDto } from '../models';

@Injectable({ providedIn: 'root' })
export class DecisionService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/maintenance-requests';

  submit(id: string, dto: DecisionRequestDto): Observable<DecisionDto> {
    return this.http.post<DecisionDto>(`${this.base}/${id}/decisions`, dto);
  }

  list(id: string): Observable<DecisionDto[]> {
    return this.http.get<DecisionDto[]>(`${this.base}/${id}/decisions`);
  }
}
