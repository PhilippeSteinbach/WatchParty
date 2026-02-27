import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  signal,
  viewChildren,
} from '@angular/core';
import { LucideAngularModule, Video, VideoOff, Mic, MicOff, Volume2, VolumeX, X } from 'lucide-angular';
import { RemotePeer } from '../models/room.model';

export interface TileEntry {
  id: string;
  type: 'local' | 'remote';
  peer?: RemotePeer;
}

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

  /** User-defined tile order (array of tile IDs). Empty = default order. */
  private readonly tileOrder = signal<string[]>([]);

  /** Drag state */
  readonly dragIndex = signal<number | null>(null);
  readonly dropTargetIndex = signal<number | null>(null);

  /** Ordered tiles combining local + remote, respecting user reorder */
  readonly orderedTiles = computed<TileEntry[]>(() => {
    const tiles: TileEntry[] = [];
    if (this.localStream()) {
      tiles.push({ id: '__local__', type: 'local' });
    }
    for (const peer of this.remoteStreams()) {
      tiles.push({ id: peer.connectionId, type: 'remote', peer });
    }
    const order = this.tileOrder();
    if (order.length === 0) return tiles;

    const byId = new Map(tiles.map(t => [t.id, t]));
    const ordered: TileEntry[] = [];
    for (const id of order) {
      const tile = byId.get(id);
      if (tile) {
        ordered.push(tile);
        byId.delete(id);
      }
    }
    // Append any new tiles not in the saved order
    for (const tile of byId.values()) {
      ordered.push(tile);
    }
    return ordered;
  });

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

  onDragStart(index: number, event: DragEvent): void {
    this.dragIndex.set(index);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', String(index));
    }
  }

  onDragOver(index: number, event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dropTargetIndex.set(index);
  }

  onDragLeave(): void {
    this.dropTargetIndex.set(null);
  }

  onDrop(index: number, event: DragEvent): void {
    event.preventDefault();
    const from = this.dragIndex();
    if (from === null || from === index) {
      this.resetDrag();
      return;
    }
    const tiles = [...this.orderedTiles()];
    const [moved] = tiles.splice(from, 1);
    tiles.splice(index, 0, moved);
    this.tileOrder.set(tiles.map(t => t.id));
    this.resetDrag();
  }

  onDragEnd(): void {
    this.resetDrag();
  }

  private resetDrag(): void {
    this.dragIndex.set(null);
    this.dropTargetIndex.set(null);
  }
}
