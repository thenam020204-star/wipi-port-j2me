package hl4.game;

/** The event/cut-scene system (Event_*, EVENT_*, EVT_*, IT_* script VM): ported function by function; unported entry points fail loudly. */
public final class Event {
    private Event() {
    }

    public static void setQuestStatus(int step, int status) {
        throw new IllegalStateException("Event_Set_QuestStatus: later");
    }

    public static void onLoad() {
        throw new IllegalStateException("EVENT_onLoad: later");
    }
}
