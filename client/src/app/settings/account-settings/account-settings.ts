import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Check, AlertCircle } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-account-settings',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './account-settings.html',
})
export class AccountSettingsComponent implements OnInit {
  private readonly authService = inject(AuthService);

  readonly CheckIcon = Check;
  readonly AlertIcon = AlertCircle;

  readonly displayName = signal('');
  readonly email = signal('');
  readonly saving = signal(false);
  readonly success = signal('');
  readonly error = signal('');

  ngOnInit(): void {
    const user = this.authService.currentUser();
    if (user) {
      this.displayName.set(user.displayName);
      this.email.set(user.email);
    }
  }

  save(): void {
    this.saving.set(true);
    this.success.set('');
    this.error.set('');
    this.authService.updateProfile({
      displayName: this.displayName(),
      email: this.email(),
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.success.set('Profile updated successfully.');
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err.error?.message || err.statusText || 'Failed to update profile.');
      },
    });
  }
}
