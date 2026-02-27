import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { LucideAngularModule, Play, ListPlus } from 'lucide-angular';
import { VideoRecommendation } from '../models/room.model';

@Component({
  selector: 'app-search-results',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './search-results.html',
  styleUrl: './search-results.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchResultsComponent {
  readonly PlayIcon = Play;
  readonly ListPlusIcon = ListPlus;

  readonly results = input.required<VideoRecommendation[]>();
  readonly isLoading = input(false);

  readonly playNow = output<VideoRecommendation>();
  readonly addToPlaylist = output<VideoRecommendation>();

  formatDuration(seconds?: number): string {
    if (!seconds) return '';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }
}
