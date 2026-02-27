import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  input,
  output,
  signal,
  viewChildren,
} from '@angular/core';
import { LucideAngularModule, Video, VideoOff, Mic, MicOff, Volume2, VolumeX, X } from 'lucide-angular';
import { RemotePeer } from '../models/room.model';

@Component({
  selector: 'app-video-grid',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './video-grid.html',
  styleUrl: './video-grid.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VideoGridComponent {
  readonly VideoIcon = Video;
  readonly VideoOffIcon = VideoOff;
  readonly MicIcon = Mic;
  readonly MicOffIcon = MicOff;
  readonly Volume2Icon = Volume2;
  readonly VolumeXIcon = VolumeX;
  readonly XIcon = X;

  readonly localStream = input<MediaStream | null>(null);
  readonly remoteStreams = input<RemotePeer[]>([]);
  readonly isCameraOn = input(false);
  readonly isMicOn = input(false);

  readonly toggleCamera = output<void>();
  readonly toggleMic = output<void>();
  readonly stopMedia = output<void>();

  /** Locally muted remote peers (by connectionId) */
  readonly mutedPeers = signal<Set<string>>(new Set());

  private readonly localVideoRef = viewChildren<ElementRef<HTMLVideoElement>>('localVideo');
  private readonly remoteVideoRefs = viewChildren<ElementRef<HTMLVideoElement>>('remoteVideo');

  constructor() {
    // Attach local stream to the local video element (always muted to prevent self-echo)
    effect(() => {
      const stream = this.localStream();
      const refs = this.localVideoRef();
      if (refs.length > 0 && stream) {
        const el = refs[0].nativeElement;
        el.srcObject = stream;
        el.muted = true;
      }
    });

    // Attach remote streams to their video elements
    effect(() => {
      const peers = this.remoteStreams();
      const refs = this.remoteVideoRefs();
      const muted = this.mutedPeers();
      for (let i = 0; i < refs.length && i < peers.length; i++) {
        const el = refs[i].nativeElement;
        if (el.srcObject !== peers[i].stream) {
          el.srcObject = peers[i].stream;
        }
        el.muted = muted.has(peers[i].connectionId);
      }
    });
  }

  toggleMutePeer(connectionId: string): void {
    this.mutedPeers.update(set => {
      const next = new Set(set);
      if (next.has(connectionId)) {
        next.delete(connectionId);
      } else {
        next.add(connectionId);
      }
      return next;
    });
  }

  isPeerMuted(connectionId: string): boolean {
    return this.mutedPeers().has(connectionId);
  }
}
