package es.foco.next.runtime;

/**
 * Lista de reproducción del lo-fi de la pausa. Hay dos turnos (0 y 1, el mismo índice que {@link
 * CalmAudio#track()}): al acabar una pista se pasa al otro turno, y cada turno rota por sus pistas.
 * Así nunca suena la misma pista dos veces seguidas y cada vuelta trae una pista distinta. Lógica
 * pura (sin Android) para probarla en JVM.
 */
public final class LofiPlaylist {
    private final int[][] slots;
    private final int[] position;

    public LofiPlaylist(int[][] slots) {
        if (slots.length != 2 || slots[0].length == 0 || slots[1].length == 0)
            throw new IllegalArgumentException("dos turnos con pistas");
        this.slots = slots;
        position = new int[2];
    }

    /** Pista (índice en el catálogo) que toca ahora en ese turno. */
    public int current(int slot) {
        int[] s = slots[slot & 1];
        return s[position[slot & 1] % s.length];
    }

    /**
     * Posición guardada de cada turno (para que Android pueda cerrar la app sin volver siempre a la
     * primera pista).
     */
    public int position(int slot) {
        return position[slot & 1];
    }

    public void restore(int slot, int value) {
        position[slot & 1] = Math.max(0, value);
    }

    /**
     * Al empezar un bloqueo nuevo: avanza el turno para que no suene la misma pista que la vez
     * anterior.
     */
    public void advance(int slot) {
        position[slot & 1]++;
    }

    /** Turno siguiente tras acabar una pista; el turno que acaba avanza para la próxima vuelta. */
    public int next(int slot) {
        position[slot & 1]++;
        return (slot + 1) & 1;
    }
}
