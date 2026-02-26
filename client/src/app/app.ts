import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { UserMenuComponent } from './user-menu/user-menu';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, UserMenuComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('watch-party');
}
