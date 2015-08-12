import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.IconLoader;
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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Main implements ToolWindowFactory, FileEditorManagerListener {
    //Cards
    protected final String CARD_LOADING_TESTS = "loading";
    protected final String CARD_NO_TESTS      = "no_tests";
    protected final String CARD_TESTS         = "tests";

    //Stuff that needs to be accessed from other places
    protected HintTextField search_field = new HintTextField(Constants.SEARCH_HINT);
    protected JPanel main_panel = new JPanel();
    protected JPanel tests_panel = new JPanel();
    protected Loader loader = new Loader();
    protected CardLayout layout = new CardLayout();
    protected Project project;
    protected TestSelector testSelector;
    protected ComboBox test_type;

    /**
     * Initialize the tool window
     * @param project the current project
     * @param toolWindow the tool window which should be initialized
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        this.project = project;
        main_panel.setLayout(layout);
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        Component component = toolWindow.getComponent();
        component.getParent().setLayout(new BorderLayout());
        component.getParent().add(main_panel);
        initCommonViews();
        component.getParent().add(test_type, BorderLayout.NORTH);
        loadingTests();
        initNoTests();
        initTests();
        testSelector = new TestSelectorEspresso(tests_panel, search_field, project);
        loader.start_animation();
        populate();
    }

    private void initCommonViews() {
        test_type = new ComboBox(new String[]{"Espresso", "Junit"});
        test_type.setBorder(null);
        test_type.addItemListener(e -> {
            if (test_type.getSelectedIndex() == 0) {
                testSelector = new TestSelectorEspresso(tests_panel, search_field, project);
                populate();
            } else {
                testSelector = new TestSelectorJUnit(tests_panel, search_field, project);
                populate();
            }
        });
    }

    /**
     * Function used to get all the tests asynchronously
     */
    public void populate() {
        AsyncTask task = new AsyncTask() {
            @Override
            protected void onPreExecute() { //show loading animation while list is being populated
                loader.start_animation();
                SwingUtilities.invokeLater(() -> layout.show(main_panel, CARD_LOADING_TESTS));
            }

            @Override
            protected void doInBackground() { //fill the panel with the tests
                SwingUtilities.invokeLater(testSelector::fillTests);
            }

            @Override
            protected void onPostExecute() { //show population results
                SwingUtilities.invokeLater(() -> {
                    loader.stop_animation();
                    if (testSelector.getTests() > 0)
                        layout.show(main_panel, CARD_TESTS);
                    else
                        layout.show(main_panel, CARD_NO_TESTS);
                });
            }
        };
        new Thread(task).start();
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
        search_field.setBackground(new JBColor(new Color(0, 0, 0, 0), new Color(255, 255, 255, 0)));
        search_field.setBorder(null);
        search_field.getDocument().addDocumentListener(searchListener(tests_panel));

        //http://www.programcreek.com/java-api-examples/index.php?api=com.intellij.openapi.util.IconLoader
        final Icon icon= IconLoader.getIcon("/actions/close.png");
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
        cleartxt_btn.setBackground(new JBColor(new Color(0, 0, 0, 0), new Color(255, 255, 255, 0)));
        cleartxt_btn.addActionListener((ActionEvent e) -> search_field.setText(""));
        cleartxt_btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        cleartxt_btn.setOpaque(false);
        cleartxt_btn.setBorderPainted(false);
        cleartxt_btn.setBackground(new JBColor(JBColor.WHITE, search_field.getBackground()));
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
        refresh_btn.addActionListener((ActionEvent e) -> populate());
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

    /**
     * Filters the tests by query string
     * @param e the document event generated by the documentlistener of the textfield document
     */
    public void updateTests(DocumentEvent e, JPanel tests_panel){
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
    public DocumentListener searchListener(JPanel tests_panel){
        return  new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTests(e, tests_panel);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTests(e, tests_panel);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
        };
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile virtualFile) {}

    @Override
    public void fileClosed(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile virtualFile) {
        layout.show(main_panel, CARD_NO_TESTS);
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent fileEditorManagerEvent) {
        populate();
    }
}
