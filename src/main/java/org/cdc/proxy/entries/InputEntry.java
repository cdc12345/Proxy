package org.cdc.proxy.entries;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.mcreator.preferences.PreferencesEntry;

import javax.swing.*;
import java.awt.*;
import java.util.EventObject;
import java.util.function.Consumer;

/**
 * e-mail: 3154934427@qq.com
 * InputMethod
 *
 * @author cdc123
 * @classname InputEntry
 * @date 2024/6/10 17:51
 */
public class InputEntry extends PreferencesEntry<String> {

    private boolean pass;

    public InputEntry(String id, String value) {
        this(id,value,false);
    }

    public InputEntry(String id, String value,boolean pass) {
        super(id, value);
        this.pass = pass;
    }

    @Override
    public JComponent getComponent(Window parent, Consumer<EventObject> fct) {
        if (pass){
            JPasswordField passwordField = new JPasswordField();
            passwordField.setText(value);
            return passwordField;
        } else {
            JTextField jTextField = new JTextField();
            jTextField.setText(value);
            return jTextField;
        }
    }

    @Override
    public void setValueFromComponent(JComponent component) {
        this.value = ((JTextField) component).getText();
    }

    @Override
    public void setValueFromJsonElement(JsonElement object) {
        this.value = object.getAsString();
    }

    @Override
    public JsonElement getSerializedValue() {
        return new JsonPrimitive(value);
    }

    public void setPass(boolean pass) {
        this.pass = pass;
    }

    public boolean isPass() {
        return pass;
    }
}
