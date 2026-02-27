import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { VideoRecommendation } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class YoutubeSearchService {
  private readonly http = inject(HttpClient);

  search(query: string, limit = 10): Observable<VideoRecommendation[]> {
    if (!query?.trim()) return of([]);
    return this.http
      .get<VideoRecommendation[]>('/api/videos/search', { params: { q: query, limit } })
      .pipe(catchError(() => of([])));
  }

  suggest(query: string): Observable<string[]> {
    if (!query?.trim()) return of([]);
    return this.http
      .get<string[]>('/api/videos/suggest', { params: { q: query } })
      .pipe(catchError(() => of([])));
  }
}
