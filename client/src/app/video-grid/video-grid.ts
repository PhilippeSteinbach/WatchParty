import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  input,
  viewChildren,
} from '@angular/core';
import { RemotePeer } from '../models/room.model';

@Component({
  selector: 'app-video-grid',
  standalone: true,
  templateUrl: './video-grid.html',
  styleUrl: './video-grid.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VideoGridComponent {
  readonly localStream = input<MediaStream | null>(null);
  readonly remoteStreams = input<RemotePeer[]>([]);

  readonly gridSize = computed(() => {
    const count = (this.localStream() ? 1 : 0) + this.remoteStreams().length;
    return Math.min(count, 6);
  });

  private readonly localVideoRef = viewChildren<ElementRef<HTMLVideoElement>>('localVideo');
  private readonly remoteVideoRefs = viewChildren<ElementRef<HTMLVideoElement>>('remoteVideo');

  constructor() {
    // Attach local stream to the local video element
    effect(() => {
      const stream = this.localStream();
      const refs = this.localVideoRef();
      if (refs.length > 0 && stream) {
        refs[0].nativeElement.srcObject = stream;
      }
    });

    // Attach remote streams to their video elements
    effect(() => {
      const peers = this.remoteStreams();
      const refs = this.remoteVideoRefs();
      for (let i = 0; i < refs.length && i < peers.length; i++) {
        const el = refs[i].nativeElement;
        if (el.srcObject !== peers[i].stream) {
          el.srcObject = peers[i].stream;
        }
      }
    });
  }
}
