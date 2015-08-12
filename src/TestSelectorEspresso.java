import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TestSelectorEspresso extends TestSelector {

    public TestSelectorEspresso(JPanel tests_panel, HintTextField search_field, Project project) {
        super(tests_panel, search_field, project);
    }

    @Override
    public void fillTests() {
        search_field.setText("");
        tests_panel.removeAll();
        Pattern test_pattern = Pattern.compile("\\btest_.*\\b[(]", Pattern.CASE_INSENSITIVE);
        Pattern suppress_pattern = Pattern.compile("@Suppress\\b", Pattern.CASE_INSENSITIVE);
        this.tests = 0;
        Boolean is_suppressed = false;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if(editor != null) {
            Document document = editor.getDocument();
            String line;
            int offset = 0;
            while ((line = getDocumentLine(document, offset)) != null) {
                offset += line.length() + 1;
                Matcher m = test_pattern.matcher(line);
                Matcher suppress_matcher = suppress_pattern.matcher(line);
                if (suppress_matcher.find()) {
                    is_suppressed = true;
                } else if (m.find()) {  //if test is exactly after suppress then test is suppressed else it is not
                    String s = m.group();
                    JCheckBox test_check = new JCheckBox(s.substring(0, s.length() - 1));
                    if (!is_suppressed)
                        test_check.setSelected(true);
                    this.tests++;
                    is_suppressed = false;
                    test_check.addItemListener((ItemEvent e) -> {
                        if (!test_check.isSelected()) {
                            ApplicationManager.getApplication().runWriteAction(() ->
                                            addSuppressed(test_check.getText())
                            );
                        } else {
                            ApplicationManager.getApplication().runWriteAction(() ->
                                            removeSuppressed(test_check.getText())
                            );
                        }
                    });
                    JPopupMenu popmenu = new JPopupMenu();
                    JMenuItem item = new JMenuItem(Constants.BTN_GOTO);
                    item.addActionListener((ActionEvent e) -> gotoText(test_check.getText()));
                    popmenu.add(item);
                    test_check.setComponentPopupMenu(popmenu);

                    tests_panel.add(test_check);
                } else {
                    is_suppressed = false;
                }
            }
        }
    }
}
