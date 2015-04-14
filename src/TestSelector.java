import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestSelector implements FileEditorManagerListener, ToolWindowFactory {

    private final String CARD_LOADING_TESTS = "loading";
    private final String CARD_NO_TESTS      = "no_tests";
    private final String CARD_TESTS         = "tests";
    private JPanel main_panel = new JPanel();
    private JPanel tests_panel = new JPanel();
    private Loader loader = new Loader();
    private CardLayout layout = new CardLayout();
    private MessageBusConnection connection;
    private int tests = 0;
    private Project project;

    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        this.project = project;
        main_panel.setLayout(layout);

        connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);

        Component component = toolWindow.getComponent();
        component.getParent().setLayout(new BorderLayout());
        component.getParent().add(main_panel);

        loadingTests();
        initNoTests();
        initTests();
        loader.start_animation();
        populate("", FileEditorManager.getInstance(project).getSelectedFiles()[0]);
    }
    private void populate(String s, VirtualFile vf) {
        AsyncTask task = new AsyncTask() {
            @Override
            protected void onPreExecute() {
                loader.start_animation();
                SwingUtilities.invokeLater(() -> layout.show(main_panel, CARD_LOADING_TESTS));
            }

            @Override
            protected void doInBackground(){
                fillTests(s, vf);
            }

            @Override
            protected void onPostExecute() {
                loader.stop_animation();
                if(tests > 0)
                    SwingUtilities.invokeLater(() -> layout.show(main_panel, CARD_TESTS));
                else
                    SwingUtilities.invokeLater(() -> layout.show(main_panel, CARD_NO_TESTS));
            }
        };
        new Thread(task).start();
    }

    private void fillTests(String search, VirtualFile vf) {
        tests_panel.removeAll();
        Pattern test_pattern = Pattern.compile("\\btest_.*\\b[(]", Pattern.CASE_INSENSITIVE);
        Pattern suppress_pattern = Pattern.compile("@Suppress\\b", Pattern.CASE_INSENSITIVE);
        this.tests = 0;
        Boolean is_suppressed = false;
        try {
            BufferedReader bf = new BufferedReader(new InputStreamReader(vf.getInputStream(), "UTF-8"));
            String line;
            while ( (line = bf.readLine()) != null) {
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
                    test_check.addItemListener(new ItemListener() {
                        @Override
                        public void itemStateChanged(ItemEvent e) {
                            if (!test_check.isSelected()) {
                                ApplicationManager.getApplication().runWriteAction(() ->
                                                addSuppressed(vf, test_check.getText())
                                );
                            } else {
                                ApplicationManager.getApplication().runWriteAction(() ->
                                                removeSuppressed(vf, test_check.getText())
                                );
                            }
                        }
                    });
                    tests_panel.add(test_check);
                }else{
                    is_suppressed = false;
                }
            }
            bf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getDocumentLine(Document document, int start){
        int end = start+1;
        String s;
        while((s = document.getText(new TextRange(start, end))).charAt(s.length()-1) != '\n')
            end++;
        return s.substring(0, s.length()-1);
    }

    /**
     * Add @Suppressed annotation
     * */
    private void addSuppressed(VirtualFile vf, String test) {
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

    /**
     * Remove @Suppressed annotation
     * */
    private void removeSuppressed(VirtualFile vf, String test) {
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
            CommandProcessor.getInstance().executeCommand(project, () -> {
                document.deleteString(finalLine_num - supress_offset, finalLine_num);
            }, "", "");
        }
    }

    private void initTests(){
        JPanel panel_btns = new JPanel();
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        tests_panel.setLayout(new BoxLayout(tests_panel, BoxLayout.PAGE_AXIS));
        JBScrollPane scrollPane = new JBScrollPane(tests_panel);
        scrollPane.getVerticalScrollBar().setBlockIncrement(16);
        JLabel label = new JLabel("Choose the tests you want to run: ");
        label.setFont(label.getFont().deriveFont(15f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(panel_btns, BorderLayout.SOUTH);
        panel.add(scrollPane);
        JButton all_btn = new JButton("Select All");
        JButton none_btn = new JButton("Select None");
        JButton refresh_btn = new JButton("Refresh");
        panel_btns.setLayout(new GridLayout(3,1));
        all_btn.addActionListener((ActionEvent e)->{
                    for (int i = 0; i < tests_panel.getComponentCount(); i++) {
                        ((JCheckBox) tests_panel.getComponents()[i]).setSelected(true);
                    }
                }
        );
        none_btn.addActionListener((ActionEvent e)->{
                    for (int i = 0; i < tests_panel.getComponentCount(); i++) {
                        ((JCheckBox) tests_panel.getComponents()[i]).setSelected(false);
                    }
                }
        );
        refresh_btn.addActionListener((ActionEvent e)->
                        populate("", FileEditorManager.getInstance(project).getSelectedFiles()[0])
        );
        panel_btns.add(all_btn);
        panel_btns.add(none_btn);
        panel_btns.add(refresh_btn);
        main_panel.add(panel, CARD_TESTS);
    }

    private void initNoTests(){
        JPanel panel_btns = new JPanel();
        panel_btns.setLayout(new GridLayout(1,1));
        JButton refresh_btn = new JButton("Refresh");
        refresh_btn.addActionListener((ActionEvent e) ->
                        populate("", FileEditorManager.getInstance(project).getSelectedFiles()[0])
        );
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        JLabel label = new JLabel("No tests to show :(");
        label.setFont(label.getFont().deriveFont(15f));
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setVerticalAlignment(JLabel.CENTER);
        panel.add(label);
        panel_btns.add(refresh_btn);
        panel.add(panel_btns, BorderLayout.SOUTH);
        main_panel.add(panel, CARD_NO_TESTS);
    }

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
    public void fileOpened(FileEditorManager fileEditorManager, VirtualFile virtualFile) {

    }

    @Override
    public void fileClosed(FileEditorManager fileEditorManager, VirtualFile virtualFile) {
        layout.show(main_panel, CARD_NO_TESTS);
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent fileEditorManagerEvent) {
        populate("", FileEditorManager.getInstance(project).getSelectedFiles()[0]);
    }
}
