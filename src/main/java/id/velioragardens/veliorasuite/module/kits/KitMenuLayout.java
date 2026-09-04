package id.velioragardens.veliorasuite.module.kits;
import java.util.Set;

/** Keeps kit icons out of navigation slots and away from each other. */
final class KitMenuLayout {
    static int slot(String id, boolean premium, int requested, Set<Integer> used) {
        if (!premium) requested = switch (id) {
            case "starter" -> 20;
            case "build" -> 22;
            case "food" -> 24;
            default -> requested;
        };
        if (available(requested,premium,used)
                && (premium || id.equals("starter") || id.equals("build") || id.equals("food")
                || (requested!=20 && requested!=22 && requested!=24))) return requested;
        for (int slot=10;slot<=43;slot++) if (available(slot,premium,used)
                && (premium || (slot!=20 && slot!=22 && slot!=24))) return slot;
        return -1;
    }
    private static boolean available(int slot, boolean premium, Set<Integer> used) {
        return slot>=10 && slot<=43 && slot%9!=0 && slot%9!=8
                && (premium || slot!=31) && !used.contains(slot);
    }
}
