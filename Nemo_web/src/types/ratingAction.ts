export type RatingAction =
  | { type: 'graduate' }
  | { type: 'requeue'; nextStep: number; delayMins: number }
  | { type: 'leech'; action: 'skip' | 'bury_today'; fallbackDelay: number };
