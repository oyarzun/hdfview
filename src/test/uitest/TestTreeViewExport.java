package test.uitest;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;
import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.nebula.nattable.finder.widgets.SWTBotNatTable;
import org.eclipse.swtbot.swt.finder.matchers.WidgetOfType;
import org.eclipse.swtbot.swt.finder.matchers.WithRegex;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.junit.Test;

public class TestTreeViewExport extends AbstractWindowTest {

    private File createImportHDF5Dataset(String datasetname) {
        String filename = "testds";
        String file_ext = ".h5";
        String groupname = "testgroupname";
        String datasetdimsize = "8 x 64";
        File hdf_file = createHDF5File(filename);

        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "filetree wrong row count", "1", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==1);
            assertTrue("createImportHDF5Dataset() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);

            items[0].click();
            items[0].contextMenu("New").menu("Group").click();

            SWTBotShell groupShell = bot.shell("New Group...");
            groupShell.activate();
            bot.waitUntil(Conditions.shellIsActive(groupShell.getText()));

            groupShell.bot().text(0).setText(groupname);

            String val = groupShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "wrong group name", groupname, val),
                    val.equals(groupname));

            groupShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(groupShell));

            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "filetree wrong row count", "2", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==2);
            assertTrue("createImportHDF5Dataset() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("createImportHDF5Dataset() filetree is missing group '" + groupname + "'", items[0].getNode(0).getText().compareTo(groupname)==0);

            items[0].getNode(0).click();

            items[0].getNode(0).contextMenu("New").menu("Dataset").click();

            SWTBotShell datasetShell = bot.shell("New Dataset...");
            datasetShell.activate();
            bot.waitUntil(Conditions.shellIsActive(datasetShell.getText()));

            datasetShell.bot().text(0).setText(datasetname);
            datasetShell.bot().text(2).setText(datasetdimsize);

            val = datasetShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "wrong dataset name", datasetname, val),
                    val.equals(datasetname));

            val = datasetShell.bot().text(2).getText();
            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "wrong dataset dimension sizes", datasetdimsize, val),
                    val.equals(datasetdimsize));

            datasetShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(datasetShell));

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("createImportHDF5Dataset()", "filetree wrong row count", "3", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==3);
            assertTrue("createImportHDF5Dataset() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("createImportHDF5Dataset() filetree is missing group '" + groupname + "'", items[0].getNode(0).getText().compareTo(groupname)==0);
            assertTrue("createImportHDF5Dataset() filetree is missing dataset '" + datasetname + "'", items[0].getNode(0).getNode(0).getText().compareTo(datasetname)==0);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }

        return hdf_file;
    }

    private void importHDF5Dataset(File hdf_file, String importfilename) {
        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("importHDF5Dataset()", "filetree wrong row count", "3", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==3);

            items[0].getNode(0).getNode(0).click();
            items[0].getNode(0).getNode(0).contextMenu("Open").click();
            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex(".*at.*\\[.*in.*\\]");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            SWTBotShell tableShell = bot.shells()[1];
            tableShell.activate();
            bot.waitUntil(Conditions.shellIsActive(tableShell.getText()));

            final SWTBotNatTable table = new SWTBotNatTable(tableShell.bot().widget(WidgetOfType.widgetOfType(NatTable.class)));

            table.click(1, 1);
            tableShell.bot().menu("Table").menu("Import Data from Text File").click();

            SWTBotShell shell = bot.shell("Enter a file name");
            shell.activate();

            SWTBotText text = shell.bot().text();
            text.setText(importfilename);

            String val = text.getText();
            assertTrue("importHDF5Dataset() wrong file name: expected '" + importfilename + "' but was '" + val + "'",
                    val.equals(importfilename));

            shell.bot().button("   &OK   ").click();
            shell.bot().waitUntil(Conditions.shellCloses(shell));

            shell = bot.shell("Import Data");
            shell.activate();

            shell.bot().button("OK").click();
            shell.bot().waitUntil(Conditions.shellCloses(shell));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
    }

    @Test
    public void saveHDF5DatasetText() {
        String filename = "tintsize";
        String file_ext = ".h5";
        String groupsetname = "DS64BITS";
        SWTBotShell exportShell = null;
        File hdf_file = openFile(filename, file_ext.equals(".h5") ? false : true);
        File export_file = null;

        try {
            new File(workDir, groupsetname+".txt").delete();
        }
        catch (Exception ex) {}

        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("saveHDF5DatasetText()", "filetree wrong row count", "10", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==10);
            assertTrue("saveHDF5DatasetText() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("saveHDF5DatasetText() filetree is missing group '" + groupsetname + "'", items[0].getNode(0).getText().compareTo("DS08BITS")==0);

            items[0].getNode(3).click();
            items[0].getNode(3).contextMenu("Export Dataset").menu("Export Data to Text File").click();
            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex("Save Dataset Data To Text File.*");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            exportShell = bot.shells()[1];
            SWTBotText text = exportShell.bot().text();
            text.setText(groupsetname+".txt");

            String val = text.getText();
            assertTrue("saveHDF5DatasetText() wrong file name: expected '" + groupsetname+".txt" + "' but was '" + val + "'",
                    val.equals(groupsetname+".txt"));

            exportShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(exportShell));

            export_file = new File(workDir, groupsetname+".txt");
            assertTrue("File-export text file created", export_file.exists());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        finally {
            if(exportShell != null && exportShell.isOpen()) {
                exportShell.bot().menu("Close").click();
                bot.waitUntil(Conditions.shellCloses(exportShell));
            }

            try {
                closeFile(hdf_file, false);
            }
            catch (Exception ex) {}
        }
    }

    @Test
    public void saveHDF5DatasetBinary() {
        String filename = "tintsize";
        String file_ext = ".h5";
        String groupsetname = "DU64BITS";
        SWTBotShell exportShell = null;
        File hdf_file = openFile(filename, file_ext.equals(".h5") ? false : true);
        File export_file = null;

        try {
            new File(workDir, groupsetname+".bin").delete();
        }
        catch (Exception ex) {}

        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("saveHDF5DatasetBinary()", "filetree wrong row count", "10", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==10);
            assertTrue("saveHDF5DatasetBinary() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("saveHDF5DatasetBinary() filetree is missing group '" + groupsetname + "'", items[0].getNode(0).getText().compareTo("DS08BITS")==0);

            items[0].getNode(3).click();
            items[0].getNode(3).contextMenu("Export Dataset").menu("Export Data as Little Endian").click();
            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex("Save Current Data To Binary File.*");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            exportShell = bot.shells()[1];
            SWTBotText text = exportShell.bot().text();
            text.setText(groupsetname+".bin");

            String val = text.getText();
            assertTrue("saveHDF5DatasetText() wrong file name: expected '" + groupsetname+".bin" + "' but was '" + val + "'",
                    val.equals(groupsetname+".bin"));

            exportShell.bot().button("   &OK   ").click();
            bot.waitUntil(Conditions.shellCloses(exportShell));

            export_file = new File(workDir, "DU64BITS.bin");
            assertTrue("File-export binary file created", export_file.exists());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        finally {
            if(exportShell != null && exportShell.isOpen()) {
                exportShell.bot().menu("Close").click();
                bot.waitUntil(Conditions.shellCloses(exportShell));
            }

            try {
                closeFile(hdf_file, false);
            }
            catch (Exception ex) {}
        }
    }

    @Test
    public void importHDF5DatasetWithTab() {
        String filename = "testds";
        String file_ext = ".h5";
        String groupname = "testgroupname";
        String datasetname = "testdstab";
        SWTBotShell tableShell = null;
        File hdf_file = createImportHDF5Dataset("testdstab");
        importHDF5Dataset(hdf_file, "DS64BITS.ttxt");

        try {
            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("importHDF5DatasetWithTab()", "filetree wrong row count", "3", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==3);
            assertTrue("importHDF5DatasetWithTab() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("importHDF5DatasetWithTab() filetree is missing group '" + groupname + "'", items[0].getNode(0).getText().compareTo(groupname)==0);
            assertTrue("importHDF5DatasetWithTab() filetree is missing dataset '" + datasetname + "'", items[0].getNode(0).getNode(0).getText().compareTo(datasetname)==0);

            items[0].getNode(0).getNode(0).click();
            items[0].getNode(0).getNode(0).contextMenu("Open").click();
            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex(".*at.*\\[.*in.*\\]");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            tableShell = bot.shells()[1];
            tableShell.activate();
            bot.waitUntil(Conditions.shellIsActive(tableShell.getText()));

            final SWTBotNatTable table = new SWTBotNatTable(tableShell.bot().widget(WidgetOfType.widgetOfType(NatTable.class)));

            table.click(1, 1);
            String val = tableShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("importHDF5DatasetWithTab()", "wrong data", "-1", val),
                    val.equals("-1"));

            table.click(8, 1);
            val = tableShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("importHDF5DatasetWithTab()", "wrong data", "-128", val),
                    val.equals("-128"));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        finally {
            tableShell.bot().menu("Table").menu("Close").click();

            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex("Changes Detected");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            SWTBotShell saveShell = bot.shells()[2];
            saveShell.activate();
            bot.waitUntil(Conditions.shellIsActive(saveShell.getText()));
            saveShell.bot().button("Cancel").click();
            bot.waitUntil(Conditions.shellCloses(saveShell));

            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {}
        }
    }

    @Ignore
    public void importHDF5DatasetWithComma() {
        String filename = "testds";
        String file_ext = ".h5";
        String groupname = "testgroupname";
        String datasetname = "testdscomma";
        SWTBotShell tableShell = null;
        File hdf_file = createImportHDF5Dataset("testdscomma");
        importHDF5Dataset(hdf_file, "DS64BITS.xtxt");
        try {
            //switch to ViewProperties.DELIMITER_COMMA

            SWTBotTree filetree = bot.tree();
            SWTBotTreeItem[] items = filetree.getAllItems();

            items[0].getNode(0).click();
            items[0].getNode(0).contextMenu("Expand All").click();

            assertTrue(constructWrongValueMessage("importHDF5DatasetWithTab()", "filetree wrong row count", "3", String.valueOf(filetree.visibleRowCount())),
                    filetree.visibleRowCount()==3);
            assertTrue("importHDF5DatasetWithTab() filetree is missing file '" + filename + file_ext + "'", items[0].getText().compareTo(filename + file_ext)==0);
            assertTrue("importHDF5DatasetWithTab() filetree is missing group '" + groupname + "'", items[0].getNode(0).getText().compareTo(groupname)==0);
            assertTrue("importHDF5DatasetWithTab() filetree is missing dataset '" + datasetname + "'", items[0].getNode(0).getNode(0).getText().compareTo(datasetname)==0);

            items[0].getNode(0).getNode(0).click();
            items[0].getNode(0).getNode(0).contextMenu("Open").click();
            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex(".*at.*\\[.*in.*\\]");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            tableShell = bot.shells()[1];
            tableShell.activate();
            bot.waitUntil(Conditions.shellIsActive(tableShell.getText()));

            final SWTBotNatTable table = new SWTBotNatTable(tableShell.bot().widget(WidgetOfType.widgetOfType(NatTable.class)));

            table.click(1, 1);
            String val = tableShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("importHDF5DatasetWithComma()", "wrong data", "-1", val),
                    val.equals("-1"));

            table.click(8, 1);
            val = tableShell.bot().text(0).getText();
            assertTrue(constructWrongValueMessage("importHDF5DatasetWithComma()", "wrong data", "-128", val),
                    val.equals("-128"));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        catch (AssertionError ae) {
            ae.printStackTrace();
        }
        finally {
            tableShell.bot().menu("Table").menu("Close").click();

            org.hamcrest.Matcher<Shell> shellMatcher = WithRegex.withRegex("Changes Detected");
            bot.waitUntil(Conditions.waitForShell(shellMatcher));

            SWTBotShell saveShell = bot.shells()[2];
            saveShell.activate();
            bot.waitUntil(Conditions.shellIsActive(saveShell.getText()));
            saveShell.bot().button("Cancel").click();
            bot.waitUntil(Conditions.shellCloses(saveShell));

            try {
                closeFile(hdf_file, true);
            }
            catch (Exception ex) {}
        }
    }
}
