package id.velioragardens.veliorasuite.api;

public interface VelioraModule {

    String getName();

    void load();

    void enable();

    void disable();

    void reload();

    boolean isEnabled();
}
