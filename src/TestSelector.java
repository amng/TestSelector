import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;

import javax.swing.*;

public abstract class TestSelector {

    protected JPanel tests_panel = new JPanel();
    protected HintTextField search_field = new HintTextField("");
    protected int tests = 0;
    protected Project project;

    public TestSelector(){}

    public TestSelector(JPanel tests_panel, HintTextField search_field, Project project){
        setProject(project);
        setTestsPanel(tests_panel);
        setSearch_field(search_field);
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public void setTestsPanel(JPanel tests_panel) {
        this.tests_panel = tests_panel;
    }

    public void setSearch_field(HintTextField search_field){
        this.search_field = search_field;
    }

    public int getTests() {
        return tests;
    }

    /** Function used to create the list of checkboxes and add their listeners*/
    public abstract  void fillTests();

    /**
     * Retrieves a document line
     * @param document the document from which the line should be retrieved
     * @param start the starting offset of the document
     * @return the line of the document or null if it's the end of the file
     */
    protected String getDocumentLine(Document document, int start){
        int end = start+1;
        String s;
        try {
            while ((s = document.getText(new TextRange(start, end))).charAt(s.length() - 1) != '\n')
                end++;
            return s.substring(0, s.length() - 1);
        }catch (IndexOutOfBoundsException e){
            return null;
        }
    }

    /**
     * Scrolls editor to the method with the name of the test
     * @param test the test to be found
     */
    protected void gotoText(String test){
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if(editor != null) {
            Document document = editor.getDocument();
            String line;
            int offset = 0;
            while (!(line = getDocumentLine(document, offset)).contains(test + "(")) {
                offset += line.length() + 1;
            }
            editor.getScrollingModel().scrollTo(new LogicalPosition(document.getLineNumber(offset), 0), ScrollType.MAKE_VISIBLE);
            editor.offsetToVisualPosition(offset);
            editor.getCaretModel().moveToOffset(offset);
        }
    }

    /** Add @Suppressed annotation */
    protected void addSuppressed(String test) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        Document document = editor.getDocument();
        String line;
        int offset = 0;
        int supress_offset = "@Suppress".length()+1;
        while (!(line = getDocumentLine(document, offset)).contains(test + "(")) {
            offset += line.length()+1;
        }
        final int finalLine_num = offset;
        if (!document.getText(new TextRange(offset-supress_offset, offset)).contains("@Suppress")) {
            CommandProcessor.getInstance().executeCommand(project, () -> {
                document.insertString(finalLine_num - 1, "\n");
                document.insertString(finalLine_num, "\t@Suppress");
            }, "", "");
        }
    }

    /** Remove @Suppressed annotation */
    protected void removeSuppressed(String test) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        Document document = editor.getDocument();
        String line;
        int offset = 0;
        int supress_offset = "\t@Suppress".length()+1;
        while (!(line = getDocumentLine(document, offset)).contains(test + "(")) {
            offset += line.length()+1;
        }
        final int finalLine_num = offset;
        if (document.getText(new TextRange(offset-supress_offset, offset)).contains("@Suppress")) {
            CommandProcessor.getInstance().executeCommand(project, () ->
                    document.deleteString(finalLine_num - supress_offset, finalLine_num)
                    , "", "");
        }
    }
}

