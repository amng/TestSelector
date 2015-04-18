import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

public class HintTextField extends JTextField{
    private String hint = "";

    public HintTextField(String hint){
        this.hint = hint;
    }

    @Override
    public void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        if(this.getText().length() == 0){
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setColor(new JBColor(Gray._200, Gray._150));
            g2.drawString(hint, 4, 17);
        }
    }
}
