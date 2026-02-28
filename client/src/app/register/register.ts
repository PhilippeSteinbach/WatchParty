import { Component, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LucideAngularModule, Film, Check, Circle } from 'lucide-angular';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, LucideAngularModule],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly FilmIcon = Film;
  readonly CheckIcon = Check;
  readonly CircleIcon = Circle;

  readonly email = signal('');
  readonly displayName = signal('');
  readonly password = signal('');
  readonly loading = signal(false);
  readonly error = signal('');

  readonly passwordRules = computed(() => {
    const pw = this.password();
    return [
      { label: 'At least 8 characters', met: pw.length >= 8 },
      { label: 'One uppercase letter', met: /[A-Z]/.test(pw) },
      { label: 'One lowercase letter', met: /[a-z]/.test(pw) },
      { label: 'One digit', met: /\d/.test(pw) },
      { label: 'One special character', met: /[^A-Za-z0-9]/.test(pw) },
    ];
  });

  register(): void {
    if (!this.email() || !this.displayName() || !this.password()) return;
    this.loading.set(true);
    this.error.set('');
    this.authService.register({
      email: this.email(),
      displayName: this.displayName(),
      password: this.password()
    }).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err) => {
        this.error.set(err.error?.detail ?? 'Registration failed. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
