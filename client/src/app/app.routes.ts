import { Routes } from '@angular/router';
import { HomeComponent } from './home/home';
import { JoinRoomComponent } from './join-room/join-room';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'room/:code', component: JoinRoomComponent },
];
