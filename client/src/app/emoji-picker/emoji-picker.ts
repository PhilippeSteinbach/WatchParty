import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'app-emoji-picker',
  standalone: true,
  templateUrl: './emoji-picker.html',
  styleUrl: './emoji-picker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmojiPickerComponent {
  readonly messageId = input.required<string>();
  readonly reactionSelected = output<{ messageId: string; emoji: string }>();

  readonly emojis = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

  selectEmoji(emoji: string): void {
    this.reactionSelected.emit({ messageId: this.messageId(), emoji });
  }
}
