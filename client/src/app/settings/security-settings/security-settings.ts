import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Check, AlertCircle } from 'lucide-angular';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-security-settings',
  standalone: true,
  imports: [FormsModule, LucideAngularModule],
  templateUrl: './security-settings.html',
})
export class SecuritySettingsComponent {
  private readonly authService = inject(AuthService);

  readonly CheckIcon = Check;
  readonly AlertIcon = AlertCircle;

  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly confirmPassword = signal('');
  readonly saving = signal(false);
  readonly success = signal('');
  readonly error = signal('');

  save(): void {
    if (this.newPassword() !== this.confirmPassword()) {
      this.error.set('Passwords do not match.');
      return;
    }
    this.saving.set(true);
    this.success.set('');
    this.error.set('');
    this.authService.changePassword({
      currentPassword: this.currentPassword(),
      newPassword: this.newPassword(),
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.success.set('Password changed successfully.');
        this.currentPassword.set('');
        this.newPassword.set('');
        this.confirmPassword.set('');
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err.error?.message || err.statusText || 'Failed to change password.');
      },
    });
  }
}
