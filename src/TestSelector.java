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

public class TestSelector implements FileEditorManagerListener, ToolWindowFactory {
    //Cards
    private final String CARD_LOADING_TESTS = "loading";
    private final String CARD_NO_TESTS      = "no_tests";
    private final String CARD_TESTS         = "tests";

    //Stuff that needs to be accessed from other places
    private HintTextField search_field = new HintTextField(Constants.SEARCH_HINT);
    private JPanel main_panel = new JPanel();
    private JPanel tests_panel = new JPanel();
    private Loader loader = new Loader();
    private CardLayout layout = new CardLayout();
    private int tests = 0;
    private Project project;

    /**
     * Initialize the tool window
     * @param project the current project
     * @param toolWindow the tool window which should be initialized
     */
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        this.project = project;
        main_panel.setLayout(layout);
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        Component component = toolWindow.getComponent();
        component.getParent().setLayout(new BorderLayout());
        component.getParent().add(main_panel);
        loadingTests();
        initNoTests();
        initTests();
        loader.start_animation();
        populate();
    }

    /**
     * Function used to get all the tests asynchronously
     */
    private void populate() {
        AsyncTask task = new AsyncTask() {
            @Override
            protected void onPreExecute() { //show loading animation while list is being populated
                loader.start_animation();
                SwingUtilities.invokeLater(() -> layout.show(main_panel, CARD_LOADING_TESTS));
            }

            @Override
            protected void doInBackground() { //fill the panel with the tests
                SwingUtilities.invokeLater(() -> fillTests());
            }

            @Override
            protected void onPostExecute() { //show population results
                SwingUtilities.invokeLater(() -> {
                    loader.stop_animation();
                    if (tests > 0)
                        layout.show(main_panel, CARD_TESTS);
                    else
                        layout.show(main_panel, CARD_NO_TESTS);
                });
            }
        };
        new Thread(task).start();
    }


    /**
     * Function used to create the list of checkboxes and add their listeners
     */
    private void fillTests() {
        search_field.setText("");
        tests_panel.removeAll();
        Pattern test_pattern = Pattern.compile("\\btest_.*\\b[(]", Pattern.CASE_INSENSITIVE);
        Pattern suppress_pattern = Pattern.compile("@Suppress\\b", Pattern.CASE_INSENSITIVE);
        this.tests = 0;
        Boolean is_suppressed = false;
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        Document document = editor.getDocument();
        String line;
        int offset = 0;
        while ((line = getDocumentLine(document, offset)) != null) {
            offset += line.length()+1;
            Matcher m = test_pattern.matcher(line);
            Matcher suppress_matcher = suppress_pattern.matcher(line);
            if(suppress_matcher.find()){
                is_suppressed = true;
            }else if(m.find()){  //if test is exactly after suppress then test is suppressed else it is not
                String s = m.group();
                JCheckBox test_check = new JCheckBox(s.substring(0, s.length()-1));
                if(!is_suppressed)
                    test_check.setSelected(true);
                this.tests++;
                is_suppressed = false;
                test_check.addItemListener((ItemEvent e)->{
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
            }else{
                is_suppressed = false;
            }
        }
    }

    /**
     * Retrieves a document line
     * @param document the document from which the line should be retrieved
     * @param start the starting offset of the document
     * @return the line of the document or null if it's the end of the file
     */
    private String getDocumentLine(Document document, int start){
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
    private void gotoText(String test) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        Document document = editor.getDocument();
        String line;
        int offset = 0;
        while (!(line = getDocumentLine(document, offset)).contains(test + "(")) {
            offset += line.length()+1;
        }
        editor.getScrollingModel().scrollTo(new LogicalPosition(document.getLineNumber(offset),0), ScrollType.MAKE_VISIBLE);
        editor.offsetToVisualPosition(offset);
        editor.getCaretModel().moveToOffset(offset);
    }


    /** Add @Suppressed annotation */
    private void addSuppressed(String test) {
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
    private void removeSuppressed(String test) {
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

    /**
     * Filters the tests by query string
     * @param e the document event generated by the documentlistener of the textfield document
     */
    private void updateTests(DocumentEvent e){
        try {
            String query = e.getDocument().getText(0, e.getDocument().getLength());
            for (int i = 0; i < tests_panel.getComponentCount(); i++) {
                Pattern test_pattern = Pattern.compile(".*" + query + ".*", Pattern.CASE_INSENSITIVE);
                if(test_pattern.matcher(((JCheckBox) tests_panel.getComponents()[i]).getText()).find()){
                    tests_panel.getComponents()[i].setVisible(true);
                }else{
                    tests_panel.getComponents()[i].setVisible(false);
                }
            }
        } catch (BadLocationException e1) {
            e1.printStackTrace();
        } catch (PatternSyntaxException e2){
            //do nothing, just wait for the user to put a correct pattern
        }
    }

    /**The listenr for the search field*/
    private DocumentListener searchListener(){
        return  new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTests(e);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTests(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        };
    }

    /** Initialize the tests gui */
    private void initTests(){
        JPanel panel_search = new JPanel();
        JPanel panel_btns = new JPanel();
        JPanel panel = new JPanel();
        //INIT the main panel
        panel.setLayout(new BorderLayout());
        tests_panel.setLayout(new BoxLayout(tests_panel, BoxLayout.PAGE_AXIS));
        JBScrollPane scrollPane = new JBScrollPane(tests_panel);
        scrollPane.getVerticalScrollBar().setBlockIncrement(16);
        panel.add(scrollPane);
        //######################################################################
        //INIT the buttons panel
        JButton all_btn = new JButton(Constants.BTN_SELECT_ALL);
        JButton none_btn = new JButton(Constants.BTN_SELECT_NONE);
        JButton refresh_btn = new JButton(Constants.BTN_REFRESH);
        panel_btns.setLayout(new GridLayout(3,1));
        all_btn.addActionListener((ActionEvent e)->{
                    for (int i = 0; i < tests_panel.getComponentCount(); i++) {
                        ((JCheckBox) tests_panel.getComponents()[i]).setSelected(true);
                    }
                }
        );
        none_btn.addActionListener((ActionEvent e) -> {
                    for (int i = 0; i < tests_panel.getComponentCount(); i++) {
                        ((JCheckBox) tests_panel.getComponents()[i]).setSelected(false);
                    }
                }
        );
        refresh_btn.addActionListener((ActionEvent e) ->
                        populate()
        );
        panel_btns.add(all_btn);
        panel_btns.add(none_btn);
        panel_btns.add(refresh_btn);
        //######################################################################
        //INIT the search panel
        panel_search.setLayout(new BorderLayout());
        search_field.setOpaque(false);
        search_field.setBackground(new Color(0,0,0,0));
        search_field.getDocument().addDocumentListener(searchListener());
        search_field.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        //http://www.programcreek.com/java-api-examples/index.php?api=com.intellij.openapi.util.IconLoader
        final Icon icon=IconLoader.getIcon("/actions/close.png");
        final Icon hoveredIcon=IconLoader.getIcon("/actions/closeHovered.png");
        JButton cleartxt_btn = new JButton(icon);
        cleartxt_btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                cleartxt_btn.setIcon(hoveredIcon);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                cleartxt_btn.setIcon(icon);
            }
        });
        cleartxt_btn.setBackground(new Color(0, 0, 0, 0));
        cleartxt_btn.addActionListener((ActionEvent e) -> search_field.setText(""));
        cleartxt_btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        cleartxt_btn.setOpaque(false);
        cleartxt_btn.setBorderPainted(false);
        cleartxt_btn.setBackground(new JBColor(Color.WHITE, search_field.getBackground()));
        Box.Filler b = (Box.Filler) Box.createHorizontalStrut(5);
        b.setOpaque(false);
        panel_search.add(b, BorderLayout.WEST);
        panel_search.add(search_field);
        panel_search.add(cleartxt_btn, BorderLayout.EAST);
        panel_search.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        panel_search.setOpaque(false);
        //######################################################################
        main_panel.add(panel, CARD_TESTS);
        panel.add(panel_search, BorderLayout.NORTH);
        panel.add(panel_btns, BorderLayout.SOUTH);
    }

    /** Initialize the no tests gui */
    private void initNoTests(){
        JPanel panel_btns = new JPanel();
        panel_btns.setLayout(new GridLayout(1, 1));
        JButton refresh_btn = new JButton(Constants.BTN_REFRESH);
        refresh_btn.addActionListener((ActionEvent e) ->
                        populate()
        );
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel(Constants.NO_RESULTS);
        label.setFont(label.getFont().deriveFont(15f));
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        panel.add(label);
        panel_btns.add(refresh_btn);
        panel.add(panel_btns, BorderLayout.SOUTH);
        main_panel.add(panel, CARD_NO_TESTS);
    }

    /** Initialize the loading animation gui */
    private void loadingTests(){
        loader.setSize(new Dimension(10, 10));
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        JPanel vertical  = new JPanel();
        vertical.setLayout(new BoxLayout(vertical, BoxLayout.PAGE_AXIS));
        vertical.add(Box.createVerticalGlue());
        vertical.add(loader);
        vertical.add(Box.createVerticalGlue());

        panel.add(Box.createHorizontalGlue());
        panel.add(vertical);
        panel.add(Box.createHorizontalGlue());

        main_panel.add(panel, CARD_LOADING_TESTS);
    }



    @Override
    public void fileOpened(FileEditorManager fileEditorManager, VirtualFile virtualFile) {}

    @Override
    public void fileClosed(FileEditorManager fileEditorManager, VirtualFile virtualFile) {
        layout.show(main_panel, CARD_NO_TESTS);
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent fileEditorManagerEvent) {
        populate();
    }
}
