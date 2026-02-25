import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { VideoRecommendation } from '../models/room.model';

@Injectable({ providedIn: 'root' })
export class VideoRecommendationService {
  private readonly http = inject(HttpClient);

  getRecommendations(videoId: string, limit = 6): Observable<VideoRecommendation[]> {
    if (!videoId) return of([]);
    return this.http
      .get<VideoRecommendation[]>(`/api/videos/${videoId}/recommendations`, { params: { limit } })
      .pipe(catchError(() => of([])));
  }
}
