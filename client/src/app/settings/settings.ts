import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { LucideAngularModule, UserRound, Shield, DoorOpen, AlertTriangle } from 'lucide-angular';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
})
export class SettingsComponent {
  readonly UserIcon = UserRound;
  readonly ShieldIcon = Shield;
  readonly RoomIcon = DoorOpen;
  readonly DangerIcon = AlertTriangle;
}
