package de.maxhenkel.radio.components;

import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

public interface PlayerBooleanComponent extends Component, AutoSyncedComponent {

    boolean getValue();

    void setValue(boolean value);

    default void toggle(){
        setValue(!getValue());
    }
}
