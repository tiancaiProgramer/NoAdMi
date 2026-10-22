package io.github.miclean.module;

public final class SaveNoticeGateTest {
    public static void main(String[] args) {
        SaveNoticeGate gate = new SaveNoticeGate();
        check(gate.allow(0));
        check(!gate.allow(1));
        check(!gate.allow(1500));
        check(!gate.allow(2999));
        check(gate.allow(3000));
        check(!gate.allow(3001));
        check(gate.allow(6000));
        check(gate.allow(1)); // Defensive monotonic-clock reset.
        System.out.println("PASS: 8 saved-notice burst and time-boundary assertions");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
