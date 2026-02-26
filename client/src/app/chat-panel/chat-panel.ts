import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WebSocketService } from '../services/websocket.service';
import { ChatMessageComponent } from '../chat-message/chat-message';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  imports: [FormsModule, ChatMessageComponent],
  templateUrl: './chat-panel.html',
  styleUrl: './chat-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPanelComponent implements AfterViewChecked {
  private readonly ws = inject(WebSocketService);

  readonly messages = this.ws.chatMessages;
  readonly messageInput = signal('');

  private readonly scrollContainer = viewChild<ElementRef<HTMLElement>>('scrollContainer');
  private shouldScroll = false;

  readonly MAX_LENGTH = 500;
  readonly emojis = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  sendMessage(): void {
    const content = this.messageInput().trim();
    if (!content || content.length > this.MAX_LENGTH) return;

    this.ws.sendChatMessage(content);
    this.messageInput.set('');
    this.shouldScroll = true;
  }

  insertEmoji(emoji: string): void {
    this.messageInput.update(text => text + emoji);
  }

  onReaction(event: { messageId: string; emoji: string }): void {
    this.ws.addReaction(event.messageId, event.emoji);
  }

  private scrollToBottom(): void {
    const el = this.scrollContainer()?.nativeElement;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }
}
