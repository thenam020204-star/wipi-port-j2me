package hl4.cpu;

/** Called when the CPU executes a host-call stub (Thumb SVC 0xFF followed by an id word). */
public interface SvcHandler {
    /**
     * Handle host call {@code id}. Arguments are in r0-r3 and on the stack.
     * The implementation writes results to r0/r1. After returning, the CPU
     * returns to LR (interworking) unless {@link Cpu#svcSetPc} was set.
     */
    void svc(Cpu cpu, int id);
}
