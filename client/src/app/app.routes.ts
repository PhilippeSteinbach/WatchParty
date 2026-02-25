import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { JoinRoomComponent } from './join-room/join-room';
import { LoginComponent } from './login/login';
import { RegisterComponent } from './register/register';
import { MyRoomsComponent } from './my-rooms/my-rooms';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'room/:code', component: JoinRoomComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'my-rooms', component: MyRoomsComponent, canActivate: [authGuard] },
];
