import { TestBed } from '@angular/core/testing';

import { YoutubePlayerComponent } from './youtube-player';

describe('YoutubePlayerComponent', () => {
  it('disables captions by default', () => {
    TestBed.configureTestingModule({
      imports: [YoutubePlayerComponent],
    });

    const fixture = TestBed.createComponent(YoutubePlayerComponent);
    const component = fixture.componentInstance;
    const playerVars = (component as unknown as { getDefaultPlayerVars: () => Record<string, unknown> }).getDefaultPlayerVars();

    expect(playerVars['cc_load_policy']).toBe(0);
  });
});
