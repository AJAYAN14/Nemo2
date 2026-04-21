
export const useFeedback = () => {
  const playSound = (isCorrect: boolean) => {
    // In a real environment, these would be local assets like /sounds/correct.mp3
    // We provide the logic here. User can replace the URLs.
    const correctUrl = 'https://assets.mixkit.co/active_storage/sfx/2000/2000-preview.mp3'; // Example subtle beep
    const errorUrl = 'https://assets.mixkit.co/active_storage/sfx/2004/2004-preview.mp3'; // Example error beep
    
    const audio = new Audio(isCorrect ? correctUrl : errorUrl);
    audio.volume = 0.3;
    audio.play().catch(e => console.warn('Audio play failed:', e));
  };

  const triggerVibrate = (isCorrect: boolean) => {
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      if (isCorrect) {
        navigator.vibrate(10); // Very short tap
      } else {
        navigator.vibrate([50, 50, 50]); // Triple pulse for error
      }
    }
  };

  const provideFeedback = (isCorrect: boolean) => {
    playSound(isCorrect);
    triggerVibrate(isCorrect);
  };

  return { provideFeedback };
};
