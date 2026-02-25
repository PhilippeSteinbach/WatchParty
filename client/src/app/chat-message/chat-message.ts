import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
  signal,
} from '@angular/core';
import { ChatMessage } from '../models/room.model';
import { EmojiPickerComponent } from '../emoji-picker/emoji-picker';

@Component({
  selector: 'app-chat-message',
  standalone: true,
  imports: [EmojiPickerComponent],
  templateUrl: './chat-message.html',
  styleUrl: './chat-message.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatMessageComponent {
  readonly message = input.required<ChatMessage>();
  readonly reactionSelected = output<{ messageId: string; emoji: string }>();

  readonly hovered = signal(false);

  relativeTime(isoDate: string): string {
    const diff = Date.now() - new Date(isoDate).getTime();
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) return 'just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }

  reactionEntries(reactions: Record<string, number>): [string, number][] {
    return Object.entries(reactions).filter(([, count]) => count > 0);
  }

  onReaction(event: { messageId: string; emoji: string }): void {
    this.reactionSelected.emit(event);
  }
}
