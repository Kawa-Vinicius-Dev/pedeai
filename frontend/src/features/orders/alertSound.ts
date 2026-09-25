/**
 * Bipe de pedido novo. O navegador só libera som depois de um clique na página, por isso o quadro tem o botão
 * "Ativar alertas", que cria o AudioContext.
 */
export class AlertSound {
  private context: AudioContext | null = null;

  enable(): boolean {
    if (this.context === null && typeof window !== 'undefined' && 'AudioContext' in window) {
      this.context = new AudioContext();
    }
    void this.context?.resume();
    return this.context !== null;
  }

  get enabled(): boolean {
    return this.context !== null;
  }

  play(): void {
    const context = this.context;
    if (!context) {
      return;
    }
    // Dois bipes curtos, agudos o bastante para ouvir com a cozinha barulhenta.
    [0, 0.35].forEach((offset) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.frequency.value = 880;
      gain.gain.setValueAtTime(0.25, context.currentTime + offset);
      gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + offset + 0.3);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(context.currentTime + offset);
      oscillator.stop(context.currentTime + offset + 0.3);
    });
  }
}
