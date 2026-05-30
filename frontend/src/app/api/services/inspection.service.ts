import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { InspectionDto, InspectionReportDto } from '../models';

@Injectable({ providedIn: 'root' })
export class InspectionService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/maintenance-requests';

  submit(id: string, dto: InspectionReportDto): Observable<InspectionDto> {
    return this.http.post<InspectionDto>(`${this.base}/${id}/inspections`, dto);
  }

  list(id: string): Observable<InspectionDto[]> {
    return this.http.get<InspectionDto[]>(`${this.base}/${id}/inspections`);
  }
}
