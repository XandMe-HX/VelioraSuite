package id.velioragardens.veliorasuite.module;

public interface Module {

    String getName();

    boolean isEnabled();

    void enable();

    void disable();

    void reload();
}
