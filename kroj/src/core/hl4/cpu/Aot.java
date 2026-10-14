package hl4.cpu;

/**
 * Statically translated (build-time) Thumb code regions. A region is entered when the
 * interpreter reaches one of its entry addresses; it runs until control leaves the region
 * and returns the next (Thumb) pc. Translation is instruction-exact (registers, flags,
 * memory, instruction count).
 */
public interface Aot {
    /** First address covered by the entry map. */
    int base();

    /** entry map: index (pc - base) >> 1 -> region id (0 = none). */
    int[] map();

    /** Run region id with the CPU state; returns next pc. */
    int run(int id, Cpu c);
}
