/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the files COPYING and Copyright.html. *
 * COPYING can be found at the root of the source code distribution tree.    *
 * Or, see https://support.hdfgroup.org/products/licenses.html               *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.DefaultNatTableStyleConfiguration;
import org.eclipse.nebula.widgets.nattable.config.EditableRule;
import org.eclipse.nebula.widgets.nattable.config.IEditableRule;
import org.eclipse.nebula.widgets.nattable.coordinate.Range;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.data.convert.DisplayConverter;
import org.eclipse.nebula.widgets.nattable.data.validate.DataValidator;
import org.eclipse.nebula.widgets.nattable.data.validate.ValidationFailedException;
import org.eclipse.nebula.widgets.nattable.grid.data.DefaultCornerDataProvider;
import org.eclipse.nebula.widgets.nattable.grid.layer.ColumnHeaderLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.CornerLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.GridLayer;
import org.eclipse.nebula.widgets.nattable.grid.layer.RowHeaderLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.layer.ILayerListener;
import org.eclipse.nebula.widgets.nattable.layer.event.ILayerEvent;
import org.eclipse.nebula.widgets.nattable.selection.SelectionLayer;
import org.eclipse.nebula.widgets.nattable.selection.event.CellSelectionEvent;
import org.eclipse.nebula.widgets.nattable.viewport.ViewportLayer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.object.DataFormat;
import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.FileFormat;
import hdf.object.HObject;
import hdf.object.ScalarDS;
import hdf.object.h5.H5Datatype;

public class DefaultScalarDSTableView extends DefaultBaseTableView implements TableView {

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultScalarDSTableView.class);

    public DefaultScalarDSTableView() {
        super();

        if (dataObject.getDatatype().getDatatypeClass() == Datatype.CLASS_REFERENCE) {
            if (dataObject.getDatatype().getDatatypeSize() > 8) {
                isReadOnly = true;
                isRegRef = true;
            }
            else
                isObjRef = true;
        }

        if ((dataObject.getDatatype().getDatatypeClass() == Datatype.CLASS_BITFIELD)
                || (dataObject.getDatatype().getDatatypeClass() == Datatype.CLASS_OPAQUE)) {
            showAsHex = true;
            checkHex.setSelection(true);
            checkScientificNotation.setSelection(false);
            checkCustomNotation.setSelection(false);
            checkBin.setSelection(false);
            showAsBin = false;
            numberFormat = normalFormat;
        }

        log.trace("DefaultScalarDSTableView: isRegRef={} isObjRef={} showAsHex={}", isRegRef, isObjRef, showAsHex);

        shell.setImage(ViewProperties.getDatasetIcon());

        shell.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                if (dataObject instanceof ScalarDS) {
                    ScalarDS ds = (ScalarDS) dataObject;

                    /*
                     * Reload the data when it is displayed next time because the display type
                     * (table or image) may be different.
                     */
                    if (ds.isImage()) ds.clearData();
                }
            }
        });
    }

    /**
     * Creates a NatTable for a Scalar dataset.
     *
     * @param parent
     *            The parent for the NatTable
     * @param dataObject
     *            The Scalar dataset for the NatTable to display
     *
     * @return The newly created NatTable
     */
    @Override
    public NatTable createTable(Composite parent, DataFormat dataObject) {
        log.trace("DefaultScalarDSTableView: createTable(): start");

        if (dataObject.getRank() <= 0) {
            try {
                ((ScalarDS) dataObject).init();
                log.trace("DefaultScalarDSTableView: createTable(): dataset inited");
            }
            catch (Exception ex) {
                Tools.showError(shell, ex.getMessage(), "createTable:" + shell.getText());
                dataValue = null;
                log.debug("DefaultScalarDSTableView: createTable(): ", ex);
                log.trace("DefaultScalarDSTableView: createTable(): finish");
                return null;
            }
        }

        // Make sure entire dataset is not loaded when looking at 3D
        // datasets using the default display mode (double clicking the
        // data object)
        if (dataObject.getRank() > 2) {
            dataObject.getSelectedDims()[dataObject.getSelectedIndex()[2]] = 1;
        }

        dataValue = null;
        try {
            dataValue = dataObject.getData();
            if (dataValue == null) {
                Tools.showError(shell, "No data read", "ScalarDS createTable:" + shell.getText());
                log.debug("DefaultScalarDSTableView: createTable(): no data read");
                log.trace("DefaultScalarDSTableView: createTable(): finish");
                return null;
            }

            log.trace("DefaultScalarDSTableView: createTable(): dataValue={}", dataValue);

            if (Tools.applyBitmask(dataValue, bitmask, bitmaskOP)) {
                isReadOnly = true;
                String opName = "Bits ";

                if (bitmaskOP == ViewProperties.BITMASK_OP.AND) opName = "Bitwise AND ";

                String title = indexBaseGroup.getText();
                title += ", " + opName + bitmask;
                indexBaseGroup.setText(title);
            }

            ((ScalarDS) dataObject).convertFromUnsignedC();
            dataValue = dataObject.getData();
        }
        catch (Throwable ex) {
            Tools.showError(shell, ex.getMessage(), "ScalarDS createTable:" + shell.getText());
            log.debug("DefaultScalarDSTableView: createTable(): ", ex);
            log.trace("DefaultScalarDSTableView: createTable(): finish");
            dataValue = null;
        }

        if (dataValue == null) {
            log.debug("DefaultScalarDSTableView: createTable(): data value is null");
            log.trace("DefaultScalarDSTableView: createTable(): finish");
            return null;
        }

        fillValue = dataObject.getFillValue();
        log.trace("DefaultScalarDSTableView: createTable(): fillValue={}", fillValue);

        String cName = dataValue.getClass().getName();
        int cIndex = cName.lastIndexOf("[");
        if (cIndex >= 0) {
            NT = cName.charAt(cIndex + 1);
        }
        log.trace("DefaultScalarDSTableView: createTable(): cName={} NT={}", cName, NT);

        // convert numerical data into char
        // only possible cases are byte[] and short[] (converted from unsigned
        // byte)
        if (isDisplayTypeChar && ((NT == 'B') || (NT == 'S'))) {
            int n = Array.getLength(dataValue);
            char[] charData = new char[n];
            for (int i = 0; i < n; i++) {
                if (NT == 'B') {
                    charData[i] = (char) Array.getByte(dataValue, i);
                }
                else if (NT == 'S') {
                    charData[i] = (char) Array.getShort(dataValue, i);
                }
            }

            dataValue = charData;
        }
        else if ((NT == 'B') && dataObject.getDatatype().getDatatypeClass() == Datatype.CLASS_ARRAY) {
            Datatype baseType = dataObject.getDatatype().getBasetype();
            if (baseType.getDatatypeClass() == Datatype.CLASS_STRING) {
                dataValue = Dataset.byteToString((byte[]) dataValue, (int) baseType.getDatatypeSize());
            }
        }

        // Create body layer
        final ScalarDSDataProvider bodyDataProvider = new ScalarDSDataProvider((ScalarDS) dataObject);
        dataLayer = new DataLayer(bodyDataProvider);
        selectionLayer = new SelectionLayer(dataLayer);
        final ViewportLayer viewportLayer = new ViewportLayer(selectionLayer);

        dataLayer.setDefaultColumnWidth(80);

        log.trace("DefaultScalarDSTableView: createTable(): rows={} : cols={}", bodyDataProvider.getRowCount(),
                bodyDataProvider.getColumnCount());

        // Create the Column Header layer
        columnHeaderDataProvider = new ScalarDSColumnHeaderDataProvider(dataObject);
        ColumnHeaderLayer columnHeaderLayer = new ColumnHeader(new DataLayer(columnHeaderDataProvider), viewportLayer,
                selectionLayer);

        // Create the Row Header layer
        rowHeaderDataProvider = new RowHeaderDataProvider(dataObject);

        // Try to adapt row height to current font
        int defaultRowHeight = curFont == null ? 20 : (2 * curFont.getFontData()[0].getHeight());

        DataLayer baseLayer = new DataLayer(rowHeaderDataProvider, 40, defaultRowHeight);
        RowHeaderLayer rowHeaderLayer = new RowHeader(baseLayer, viewportLayer, selectionLayer);

        // Create the Corner layer
        ILayer cornerLayer = new CornerLayer(
                new DataLayer(new DefaultCornerDataProvider(columnHeaderDataProvider, rowHeaderDataProvider)),
                rowHeaderLayer, columnHeaderLayer);

        // Create the Grid layer
        GridLayer gridLayer = new EditingGridLayer(viewportLayer, columnHeaderLayer, rowHeaderLayer, cornerLayer);

        final NatTable natTable = new NatTable(parent, gridLayer, false);
        natTable.addConfiguration(new DefaultNatTableStyleConfiguration());
        natTable.addLayerListener(new ScalarDSCellSelectionListener());

        // Create popup menu for region or object ref.
        if (isRegRef || isObjRef) {
            natTable.addConfiguration(new RefContextMenu(natTable));
        }

        natTable.configure();

        log.trace("DefaultScalarDSTableView: createTable(): finish");

        return natTable;
    }

    /**
     * Returns the selected data values of the ScalarDS
     */
    @Override
    public Object getSelectedData() {
        Object selectedData = null;

        // Since NatTable returns the selected row positions as a Set<Range>, convert
        // this to an Integer[]
        Set<Range> rowPositions = selectionLayer.getSelectedRowPositions();
        Set<Integer> selectedRowPos = new LinkedHashSet<>();
        Iterator<Range> i1 = rowPositions.iterator();
        while (i1.hasNext()) {
            selectedRowPos.addAll(i1.next().getMembers());
        }

        Integer[] selectedRows = selectedRowPos.toArray(new Integer[0]);
        int[] selectedCols = selectionLayer.getSelectedColumnPositions();

        if (selectedRows == null || selectedRows.length <= 0 || selectedCols == null || selectedCols.length <= 0) {
            return null;
        }

        int size = selectedCols.length * selectedRows.length;
        log.trace("DefaultScalarDSTableView: getSelectedData() data size: {}", size);

        // the whole table is selected
        if ((dataTable.getPreferredColumnCount() - 1 == selectedCols.length)
                && (dataTable.getPreferredRowCount() - 1 == selectedRows.length)) {
            return dataValue;
        }

        selectedData = null;
        if (isRegRef) {
            // reg. ref data are stored in strings
            selectedData = new String[size];
        }
        else {
            switch (NT) {
                case 'B':
                    selectedData = new byte[size];
                    break;
                case 'S':
                    selectedData = new short[size];
                    break;
                case 'I':
                    selectedData = new int[size];
                    break;
                case 'J':
                    selectedData = new long[size];
                    break;
                case 'F':
                    selectedData = new float[size];
                    break;
                case 'D':
                    selectedData = new double[size];
                    break;
                default:
                    selectedData = null;
                    break;
            }
        }

        if (selectedData == null) {
            shell.getDisplay().beep();
            Tools.showError(shell, "Unsupported data type.", shell.getText());
            return null;
        }
        log.trace("DefaultScalarDSTableView: getSelectedData(): selectedData is type {}", NT);

        int w = dataTable.getPreferredColumnCount() - 1;
        log.trace("DefaultScalarDSTableView: getSelectedData(): getColumnCount={}", w);
        int idx_src = 0;
        int idx_dst = 0;
        log.trace("DefaultScalarDSTableView: getSelectedData(): Rows.length={} Cols.length={}", selectedRows.length,
                selectedCols.length);
        for (int i = 0; i < selectedRows.length; i++) {
            for (int j = 0; j < selectedCols.length; j++) {
                idx_src = selectedRows[i] * w + selectedCols[j];
                log.trace("DefaultScalarDSTableView: getSelectedData()[{},{}]: dataValue[{}]={} from r{} and c{}", i, j,
                        idx_src, Array.get(dataValue, idx_src), selectedRows[i], selectedCols[j]);
                Array.set(selectedData, idx_dst, Array.get(dataValue, idx_src));
                log.trace("DefaultScalarDSTableView: getSelectedData()[{},{}]: selectedData[{}]={}", i, j, idx_dst,
                        Array.get(selectedData, idx_dst));
                idx_dst++;
            }
        }

        return selectedData;
    }

    /**
     * Update cell value in memory. It does not change the dataset's value in the
     * file.
     *
     * @param cellValue
     *            the string value of input.
     * @param row
     *            the row of the editing cell.
     * @param col
     *            the column of the editing cell.
     *
     * @throws Exception
     *             if a failure occurred
     */
    @Override
    protected void updateValueInMemory(String cellValue, int row, int col) throws Exception {
        log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): start", row, col);

        if ((cellValue == null) || ((cellValue = cellValue.trim()) == null)) {
            log.debug(
                    "DefaultScalarDSTableView: updateValueInMemory({}, {}): cell value not updated; new value is null",
                    row, col);
            log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): finish", row, col);
            return;
        }

        // No need to update if values are the same
        if (cellValue.equals(dataLayer.getDataValue(col, row).toString())) {
            log.debug(
                    "DefaultScalarDSTableView: updateValueInMemory({}, {}): cell value not updated; new value same as old value",
                    row, col);
            log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): finish", row, col);
            return;
        }

        try {
            int i = 0;
            if (isDataTransposed) {
                i = col * (dataTable.getPreferredRowCount() - 1) + row;
            }
            else {
                i = row * (dataTable.getPreferredColumnCount() - 1) + col;
            }

            log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): {} NT={}", row, col, cellValue, NT);

            ScalarDS sds = (ScalarDS) dataObject;
            boolean isUnsigned = sds.isUnsigned();
            String cname = sds.getOriginalClass().getName();
            char dname = cname.charAt(cname.lastIndexOf("[") + 1);
            log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): isUnsigned={} cname={} dname={}", row,
                    col, isUnsigned, cname, dname);

            switch (NT) {
                case 'B':
                    byte bvalue = 0;
                    bvalue = Byte.parseByte(cellValue);
                    Array.setByte(dataValue, i, bvalue);
                    break;
                case 'S':
                    short svalue = 0;
                    svalue = Short.parseShort(cellValue);
                    Array.setShort(dataValue, i, svalue);
                    break;
                case 'I':
                    int ivalue = 0;
                    ivalue = Integer.parseInt(cellValue);
                    Array.setInt(dataValue, i, ivalue);
                    break;
                case 'J':
                    long lvalue = 0;
                    if (dname == 'J') {
                        BigInteger big = new BigInteger(cellValue);
                        lvalue = big.longValue();
                    }
                    else
                        lvalue = Long.parseLong(cellValue);
                    Array.setLong(dataValue, i, lvalue);
                    break;
                case 'F':
                    float fvalue = 0;
                    fvalue = Float.parseFloat(cellValue);
                    Array.setFloat(dataValue, i, fvalue);
                    break;
                case 'D':
                    double dvalue = 0;
                    dvalue = Double.parseDouble(cellValue);
                    Array.setDouble(dataValue, i, dvalue);
                    break;
                default:
                    Array.set(dataValue, i, cellValue);
                    break;
            }

            isValueChanged = true;
        }
        catch (Exception ex) {
            log.debug("DefaultScalarDSTableView: updateValueInMemory({}, {}): {}", row, col, ex);
        }

        log.trace("DefaultScalarDSTableView: updateValueInMemory({}, {}): finish", row, col);
    }

    /**
     * Update dataset's value in file. The changes will go to the file.
     */
    @Override
    public void updateValueInFile() {
        log.trace("DefaultScalarDSTableView: updateValueInFile(): start");

        if (isReadOnly || !isValueChanged || showAsBin || showAsHex) {
            log.debug(
                    "DefaultScalarDSTableView: updateValueInFile(): file not updated; read-only or unchanged data or displayed as hex or binary");
            log.trace("DefaultScalarDSTableView: updateValueInFile(): finish");
            return;
        }

        try {
            log.trace("DefaultScalarDSTableView: updateValueInFile(): write");
            ((ScalarDS) dataObject).write();
        }
        catch (Exception ex) {
            shell.getDisplay().beep();
            Tools.showError(shell, ex.getMessage(), shell.getText());
            log.debug("DefaultScalarDSTableView: updateValueInFile(): ", ex);
            log.trace("DefaultScalarDSTableView: updateValueInFile(): finish");
            return;
        }

        isValueChanged = false;
        log.trace("DefaultScalarDSTableView: updateValueInFile(): finish");
    }

    /**
     * Returns an appropriate DisplayConverter to convert data values into
     * human-readable forms in the table. Also converts the human-readable form back
     * into real data when writing the data object back to the file.
     *
     * @param dataObject
     *            The data object whose values are to be converted.
     *
     * @return A new DisplayConverter if the data object is valid, or null
     *         otherwise.
     */
    @Override
    protected DisplayConverter getDataDisplayConverter(final DataFormat dataObject) {
        if (dataObject == null) return null;

        return new ScalarDSDataDisplayConverter(dataObject);
    }

    /**
     * Returns an appropriate DataValidator to check that the data entered is valid
     * before committing it to memory.
     *
     * @param theDataset
     *            The dataset being edited
     *
     * @return A new DataValidator that returns true if the data is valid and can be
     *         committed to memory, or false otherwise.
     */
    @Override
    protected DataValidator getDataValidator(final DataFormat dataObject) {
        boolean isUnsigned = ((ScalarDS) dataObject).isUnsigned();
        String cname = ((ScalarDS) dataObject).getOriginalClass().getName();

        // TODO: Add validation for array types when array editing is added

        switch (cname.charAt(cname.lastIndexOf("[") + 1)) {
            case 'B':
                if (isUnsigned) {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidUByte(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
                else {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidByte(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
            case 'S':
                if (isUnsigned) {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidUShort(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
                else {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidShort(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
            case 'I':
                if (isUnsigned) {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidUInt(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
                else {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidInt(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
            case 'J':
                if (isUnsigned) {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidULong(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
                else {
                    return new DataValidator() {
                        @Override
                        public boolean validate(int colIndex, int rowIndex, Object newValue) {
                            if (!Tools.checkValidLong(newValue.toString()))
                                throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                        + colIndex + ") to '" + newValue.toString() + "'");

                            return true;
                        }
                    };
                }
            case 'F':
                return new DataValidator() {
                    @Override
                    public boolean validate(int colIndex, int rowIndex, Object newValue) {
                        if (!Tools.checkValidFloat(newValue.toString()))
                            throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                    + colIndex + ") to '" + newValue.toString() + "'");

                        return true;
                    }
                };
            case 'D':
                return new DataValidator() {
                    @Override
                    public boolean validate(int colIndex, int rowIndex, Object newValue) {
                        if (!Tools.checkValidDouble(newValue.toString()))
                            throw new ValidationFailedException("Failed to update value at " + "(" + rowIndex + ", "
                                    + colIndex + ") to '" + newValue.toString() + "'");

                        return true;
                    }
                };
            default:
                // Default: never validate
                return new DataValidator() {
                    @Override
                    public boolean validate(int colIndex, int rowIndex, Object newValue) {
                        return false;
                    }
                };
        }
    }

    /**
     * Returns an IEditableRule that determines whether cells can be edited.
     *
     * Cells can be edited as long as the dataset is not opened in read-only mode
     * and the data is not currently displayed in hexadecimal, binary, or character
     * mode.
     *
     * @param dataObject
     *            The dataset for editing
     *
     * @return a new IEditableRule for the dataset
     */
    @Override
    protected IEditableRule getDataEditingRule(DataFormat dataObject) {
        if (dataObject == null) return null;

        return new EditableRule() {
            @Override
            public boolean isEditable(int columnIndex, int rowIndex) {
                if (isReadOnly || isDisplayTypeChar || showAsBin || showAsHex
                        || dataObject.getDatatype().getDatatypeClass() == Datatype.CLASS_ARRAY) {
                    return false;
                }
                else {
                    return true;
                }
            }
        };
    }

    /**
     * Display data pointed to by object references. Data of each object is shown in
     * a separate spreadsheet.
     *
     * @param ref
     *            the array of strings that contain the object reference
     *            information.
     *
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void showObjRefData(long ref) {
        long[] oid = { ref };
        log.trace("DefaultScalarDSTableView: showObjRefData(): start: ref={}", ref);

        HObject obj = FileFormat.findObject(((HObject) dataObject).getFileFormat(), oid);
        if (obj == null || !(obj instanceof ScalarDS)) {
            Tools.showError(shell, "Could not show object reference data: invalid or null data", shell.getText());
            log.debug("DefaultScalarDSTableView: showObjRefData(): obj is null or not a Scalar Dataset");
            log.trace("DefaultScalarDSTableView: showObjRefData(): finish");
            return;
        }

        ScalarDS dset = (ScalarDS) obj;
        ScalarDS dset_copy = null;

        // create an instance of the dataset constructor
        Constructor<? extends ScalarDS> constructor = null;
        Object[] paramObj = null;
        Object data = null;

        try {
            Class[] paramClass = { FileFormat.class, String.class, String.class };
            constructor = dset.getClass().getConstructor(paramClass);
            paramObj = new Object[] { dset.getFileFormat(), dset.getName(), dset.getPath() };
            dset_copy = constructor.newInstance(paramObj);
            data = dset_copy.getData();
        }
        catch (Exception ex) {
            log.debug("DefaultScalarDSTableView: showObjRefData(): couldn't show data: ", ex);
            Tools.showError(shell, ex.getMessage(), "Object Reference: " + shell.getText());
            data = null;
        }

        if (data == null) {
            log.trace("DefaultScalarDSTableView: showObjRefData(): finish");
            return;
        }

        Class<?> theClass = null;
        String viewName = null;

        switch (viewType) {
            case TEXT:
                viewName = (String) HDFView.getListOfTextViews().get(0);
                break;
            case IMAGE:
                viewName = HDFView.getListOfImageViews().get(0);
                break;
            case TABLE:
                viewName = (String) HDFView.getListOfTableViews().get(0);
                break;
            default:
                viewName = null;
        }

        try {
            theClass = Class.forName(viewName);
        }
        catch (Exception ex) {
            try {
                theClass = ViewProperties.loadExtClass().loadClass(viewName);
            }
            catch (Exception ex2) {
                theClass = null;
            }
        }

        // Use default dataview
        if (theClass == null) {
            log.trace("DefaultScalarDSTableView: showObjRefData(): Using default dataview");
            switch (viewType) {
                case TEXT:
                    viewName = "hdf.view.DefaultTextView";
                    break;
                case IMAGE:
                    viewName = "hdf.view.DefaultImageView";
                    break;
                case TABLE:
                    viewName = "hdf.view.DefaultTableView";
                    break;
                default:
                    viewName = null;
            }

            try {
                theClass = Class.forName(viewName);
            }
            catch (Exception ex) {
                log.debug("DefaultScalarDSTableView: showObjRefData(): no suitable display class found");
                log.trace("DefaultScalarDSTableView: showObjRefData(): finish");
                Tools.showError(shell, "Could not show reference data: no suitable display class found",
                        shell.getText());
                return;
            }
        }

        HashMap map = new HashMap(1);
        map.put(ViewProperties.DATA_VIEW_KEY.OBJECT, dset_copy);
        Object[] args = { viewer, map };

        try {
            Tools.newInstance(theClass, args);
        }
        catch (Exception ex) {
            log.debug("DefaultScalarDSTableView: showObjRefData(): Could not show reference data: ", ex);
            Tools.showError(shell, "Could not show reference data: " + ex.toString(), shell.getText());
        }

        log.trace("DefaultScalarDSTableView: showObjRefData(): finish");
    }

    /**
     * Display data pointed to by region references. Data of each region is shown in
     * a separate spreadsheet. The reg. ref. information is stored in strings of the
     * format below:
     * <p>
     * <ul>
     * <li>For point selections: "file_id:obj_id { <point1> <point2> ...) }", where
     * <point1> is in the form of (location_of_dim0, location_of_dim1, ...). For
     * example, 0:800 { (0,1) (2,11) (1,0) (2,4) }</li>
     * <li>For rectangle selections: "file_id:obj_id { <corner coordinates1> <corner
     * coordinates2> ... }", where <corner coordinates1> is in the form of
     * (start_corner)-(oposite_corner). For example, 0:800 { (0,0)-(0,2)
     * (0,11)-(0,13) (2,0)-(2,2) (2,11)-(2,13) }</li>
     * </ul>
     *
     * @param reg
     *            the array of strings that contain the reg. ref information.
     *
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void showRegRefData(String reg) {
        log.trace("DefaultScalarDSTableView: showRegRefData(): start: reg={}", reg);

        if (reg == null || (reg.length() <= 0) || (reg.compareTo("NULL") == 0)) {
            Tools.showError(shell, "Could not show region reference data: invalid or null data", shell.getText());
            log.debug("DefaultScalarDSTableView: showRegRefData(): ref is null or invalid");
            log.trace("DefaultScalarDSTableView: showRegRefData(): finish");
            return;
        }

        boolean isPointSelection = (reg.indexOf('-') <= 0);

        // find the object location
        String oidStr = reg.substring(reg.indexOf('/'), reg.indexOf(' '));
        log.trace("DefaultScalarDSTableView: showRegRefData(): isPointSelection={} oidStr={}", isPointSelection,
                oidStr);

        // decode the region selection
        String regStr = reg.substring(reg.indexOf('{') + 1, reg.indexOf('}'));
        if (regStr == null || regStr.length() <= 0) {
            Tools.showError(shell, "Could not show region reference data: no region selection made.", shell.getText());
            log.debug("DefaultScalarDSTableView: showRegRefData(): no region selection made");
            log.trace("DefaultScalarDSTableView: showRegRefData(): finish");
            return; // no selection
        }

        reg.substring(reg.indexOf('}') + 1);

        StringTokenizer st = new StringTokenizer(regStr);
        int nSelections = st.countTokens();
        if (nSelections <= 0) {
            Tools.showError(shell, "Could not show region reference data: no region selection made.", shell.getText());
            log.debug("DefaultScalarDSTableView: showRegRefData(): no region selection made");
            log.trace("DefaultScalarDSTableView: showRegRefData(): finish");
            return; // no selection
        }
        log.trace("DefaultScalarDSTableView: showRegRefData(): nSelections={}", nSelections);

        HObject obj = FileFormat.findObject(((HObject) dataObject).getFileFormat(), oidStr);
        if (obj == null || !(obj instanceof ScalarDS)) {
            Tools.showError(shell, "Could not show object reference data: invalid or null data", shell.getText());
            log.debug("DefaultScalarDSTableView: showRegRefData(): obj is null or not a Scalar Dataset");
            log.debug("DefaultScalarDSTableView: showRegRefData(): finish");
            return;
        }

        ScalarDS dset = (ScalarDS) obj;
        ScalarDS dset_copy = null;

        // create an instance of the dataset constructor
        Constructor<? extends ScalarDS> constructor = null;
        Object[] paramObj = null;
        try {
            Class[] paramClass = { FileFormat.class, String.class, String.class };
            constructor = dset.getClass().getConstructor(paramClass);
            paramObj = new Object[] { dset.getFileFormat(), dset.getName(), dset.getPath() };
        }
        catch (Exception ex) {
            log.debug("DefaultScalarDSTableView: showRegRefData(): constructor failure: ", ex);
            constructor = null;
        }

        // load each selection into a separate dataset and display it in
        // a separate spreadsheet
        StringBuffer titleSB = new StringBuffer();
        log.trace("DefaultScalarDSTableView: showRegRefData(): titleSB created");

        while (st.hasMoreTokens()) {
            log.trace("DefaultScalarDSTableView: showRegRefData(): st.hasMoreTokens() begin");
            try {
                dset_copy = constructor.newInstance(paramObj);
            }
            catch (Exception ex) {
                log.debug("DefaultScalarDSTableView: showRegRefData(): constructor newInstance failure: ", ex);
                continue;
            }

            if (dset_copy == null) {
                log.debug("DefaultScalarDSTableView: showRegRefData(): continue after null dataset copy");
                continue;
            }

            try {
                dset_copy.init();
            }
            catch (Exception ex) {
                log.debug("DefaultScalarDSTableView: showRegRefData(): continue after copied dataset init failure: ",
                        ex);
                continue;
            }

            dset_copy.getRank();
            long start[] = dset_copy.getStartDims();
            long count[] = dset_copy.getSelectedDims();

            // set the selected dimension sizes based on the region selection
            // info.
            int idx = 0;
            String sizeStr = null;
            String token = st.nextToken();

            titleSB.setLength(0);
            titleSB.append(token);
            titleSB.append(" at ");
            log.trace("DefaultScalarDSTableView: showRegRefData(): titleSB={}", titleSB);

            token = token.replace('(', ' ');
            token = token.replace(')', ' ');
            if (isPointSelection) {
                // point selection
                StringTokenizer tmp = new StringTokenizer(token, ",");
                while (tmp.hasMoreTokens()) {
                    count[idx] = 1;
                    sizeStr = tmp.nextToken().trim();
                    start[idx] = Long.valueOf(sizeStr);
                    idx++;
                }
            }
            else {
                // rectangle selection
                String startStr = token.substring(0, token.indexOf('-'));
                String endStr = token.substring(token.indexOf('-') + 1);
                StringTokenizer tmp = new StringTokenizer(startStr, ",");
                while (tmp.hasMoreTokens()) {
                    sizeStr = tmp.nextToken().trim();
                    start[idx] = Long.valueOf(sizeStr);
                    idx++;
                }

                idx = 0;
                tmp = new StringTokenizer(endStr, ",");
                while (tmp.hasMoreTokens()) {
                    sizeStr = tmp.nextToken().trim();
                    count[idx] = Long.valueOf(sizeStr) - start[idx] + 1;
                    idx++;
                }
            }
            log.trace("DefaultScalarDSTableView: showRegRefData(): selection inited");

            try {
                dset_copy.getData();
            }
            catch (Exception ex) {
                log.debug("DefaultScalarDSTableView: showRegRefData(): getData failure: ", ex);
                Tools.showError(shell, ex.getMessage(), "Region Reference: " + shell.getText());
            }

            Class<?> theClass = null;
            String viewName = null;

            switch (viewType) {
                case TEXT:
                    viewName = (String) HDFView.getListOfTextViews().get(0);
                    break;
                case IMAGE:
                    viewName = HDFView.getListOfImageViews().get(0);
                    break;
                case TABLE:
                    viewName = (String) HDFView.getListOfTableViews().get(0);
                    break;
                default:
                    viewName = null;
            }

            try {
                theClass = Class.forName(viewName);
            }
            catch (Exception ex) {
                try {
                    theClass = ViewProperties.loadExtClass().loadClass(viewName);
                }
                catch (Exception ex2) {
                    theClass = null;
                }
            }

            // Use default dataview
            if (theClass == null) {
                log.trace("DefaultScalarDSTableView: showRegRefData(): Using default dataview");
                switch (viewType) {
                    case TEXT:
                        viewName = "hdf.view.DefaultTextView";
                        break;
                    case IMAGE:
                        viewName = "hdf.view.DefaultImageView";
                        break;
                    case TABLE:
                        viewName = "hdf.view.DefaultTableView";
                        break;
                    default:
                        viewName = null;
                }

                try {
                    theClass = Class.forName(viewName);
                }
                catch (Exception ex) {
                    log.debug("DefaultScalarDSTableView: showRegRefData(): no suitable display class found");
                    log.trace("DefaultScalarDSTableView: showRegRefData(): finish");
                    Tools.showError(shell, "Could not show reference data: no suitable display class found",
                            shell.getText());
                    return;
                }
            }

            HashMap map = new HashMap(1);
            map.put(ViewProperties.DATA_VIEW_KEY.OBJECT, dset_copy);
            Object[] args = { viewer, map };

            try {
                Tools.newInstance(theClass, args);
            }
            catch (Exception ex) {
                log.debug("DefaultScalarDSTableView: showRegRefData(): Could not show reference data: ", ex);
                Tools.showError(shell, "Could not show reference data: " + ex.toString(), shell.getText());
            }

            log.trace("DefaultScalarDSTableView: showRegRefData(): st.hasMoreTokens() end");
        } // while (st.hasMoreTokens())

        log.trace("DefaultScalarDSTableView: showRegRefData(): finish");
    } // private void showRegRefData(String reg)

    /**
     * Provides the NatTable with data from a Scalar Dataset for each cell.
     */
    private class ScalarDSDataProvider implements IDataProvider {
        private Object             theValue;

        // Array used to store elements of ARRAY datatypes
        private final Object[]     arrayElements;

        // StringBuffer used to store variable-length datatypes
        private final StringBuffer buffer;

        private final Datatype     dtype;
        private final Datatype     btype;

        private final long         arraySize;

        private final long[]       dims;

        private final int          rank;

        private final boolean      isArray;
        private final boolean      isInt;
        private final boolean      isUINT64;
        private final boolean      isBitfieldOrOpaque;

        private boolean            isVLStr;

        private final boolean      isNaturalOrder;

        private final long         rowCount;
        private final long         colCount;

        public ScalarDSDataProvider(ScalarDS theDataset) {
            log.trace("ScalarDSDataProvider:NT={} start", NT);
            buffer = new StringBuffer();

            dtype = theDataset.getDatatype();
            btype = dtype.getBasetype();

            dims = theDataset.getSelectedDims();

            rank = theDataset.getRank();

            isArray = dtype.getDatatypeClass() == Datatype.CLASS_ARRAY;
            log.trace("ScalarDSDataProvider:isArray={} start", isArray);
            isInt = (NT == 'B' || NT == 'S' || NT == 'I' || NT == 'J');
            if (isArray)
                isUINT64 = (btype.isUnsigned() && (NT == 'J'));
            else
                isUINT64 = (dtype.isUnsigned() && (NT == 'J'));
            isBitfieldOrOpaque = (dtype.getDatatypeClass() == Datatype.CLASS_OPAQUE
                    || dtype.getDatatypeClass() == Datatype.CLASS_BITFIELD);

            isNaturalOrder = (theDataset.getRank() == 1
                    || (theDataset.getSelectedIndex()[0] < theDataset.getSelectedIndex()[1]));

            if (isArray) {
                if (dtype.isVLEN() && btype.getDatatypeClass() == Datatype.CLASS_STRING) {
                    isVLStr = true;

                    // Variable-length string arrays don't have a defined array size
                    arraySize = dtype.getArrayDims()[0];
                }
                else if (btype.getDatatypeClass() == Datatype.CLASS_ARRAY) {
                    // Array of Array
                    long[] dims = btype.getArrayDims();

                    long size = 1;
                    for (int i = 0; i < dims.length; i++) {
                        size *= dims[i];
                    }

                    arraySize = size * (dtype.getDatatypeSize() / btype.getDatatypeSize());
                }
                else if (isBitfieldOrOpaque) {
                    arraySize = dtype.getDatatypeSize();
                }
                else {
                    arraySize = dtype.getDatatypeSize() / btype.getDatatypeSize();
                }

                arrayElements = new Object[(int) arraySize];
            }
            else {
                if (dtype.isVLEN() && dtype.getDatatypeClass() == Datatype.CLASS_STRING) isVLStr = true;

                arraySize = 0;
                arrayElements = null;
            }

            if (theDataset.getRank() > 1) {
                rowCount = theDataset.getHeight();
                colCount = theDataset.getWidth();
            }
            else {
                rowCount = (int) dims[0];
                colCount = 1;
            }
            log.trace("ScalarDSDataProvider) finish");
        }

        @Override
        public Object getDataValue(int columnIndex, int rowIndex) {
            log.trace("ScalarDSDataProvider:getValueAt({},{}) start", rowIndex, columnIndex);
            log.trace("ScalarDSDataProvider:getValueAt isInt={} isArray={} showAsHex={} showAsBin={}", isInt, isArray,
                    showAsHex, showAsBin);

            if (isArray) {
                log.trace("ScalarDSDataProvider:getValueAt ARRAY dataset size={} isDisplayTypeChar={} isUINT64={}",
                        arraySize, isDisplayTypeChar, isUINT64);

                int index = (int) (rowIndex * colCount + columnIndex) * (int) arraySize;

                if (isDisplayTypeChar) {
                    for (int i = 0; i < arraySize; i++) {
                        arrayElements[i] = Array.getChar(dataValue, index++);
                    }

                    theValue = arrayElements;
                }
                else if (isVLStr) {
                    buffer.setLength(0);

                    for (int i = 0; i < dtype.getArrayDims()[0]; i++) {
                        if (i > 0) buffer.append(", ");
                        buffer.append(Array.get(dataValue, index++));
                    }

                    theValue = buffer.toString();
                }
                else if (isBitfieldOrOpaque) {
                    for (int i = 0; i < arraySize; i++) {
                        arrayElements[i] = Array.getByte(dataValue, index++);
                    }

                    theValue = arrayElements;
                }
                else {
                    if (isUINT64) {
                        for (int i = 0; i < arraySize; i++) {
                            arrayElements[i] = Tools.convertUINT64toBigInt(Array.getLong(dataValue, index++));
                        }
                    }
                    else {
                        for (int i = 0; i < arraySize; i++) {
                            arrayElements[i] = Array.get(dataValue, index++);
                        }
                    }

                    theValue = arrayElements;
                }
            }
            else {
                long index = columnIndex * rowCount + rowIndex;

                if (rank > 1) {
                    log.trace("ScalarDSDataProvider:getValueAt rank={} isDataTransposed={} isNaturalOrder={}",
                            rank, isDataTransposed, isNaturalOrder);
                    if (isDataTransposed && isNaturalOrder)
                        index = columnIndex * rowCount + rowIndex;
                    else if (!isDataTransposed && !isNaturalOrder)
                        // Reshape Data
                        index = rowIndex * colCount + columnIndex;
                    else if (isDataTransposed && !isNaturalOrder)
                        // Transpose Data
                        index = columnIndex * rowCount + rowIndex;
                    else
                        index = rowIndex * colCount + columnIndex;
                }

                if (isBitfieldOrOpaque) {
                    int len = (int) dtype.getDatatypeSize();
                    byte[] elements = new byte[len];

                    index *= len;

                    for (int i = 0; i < len; i++) {
                        elements[i] = Array.getByte(dataValue, (int) index + i);
                    }

                    theValue = elements;
                }
                else {
                    if (isUINT64) {
                        theValue = Tools.convertUINT64toBigInt(Array.getLong(dataValue, (int) index));
                    }
                    else {
                        theValue = Array.get(dataValue, (int) index);
                    }
                }
            }

            log.trace("ScalarDSDataProvider:getValueAt {} finish", theValue);
            return theValue;
        }

        @Override
        public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
            try {
                updateValueInMemory((String) newValue, rowIndex, columnIndex);
            }
            catch (Exception ex) {
                log.debug("ScalarDSDataProvider:setDataValue({}, {}) failure: ", rowIndex, columnIndex, ex);
            }
        }

        @Override
        public int getColumnCount() {
            return (int) colCount;
        }

        @Override
        public int getRowCount() {
            return (int) rowCount;
        }
    }

    private class ScalarDSDataDisplayConverter extends DisplayConverter {
        private final StringBuffer buffer;

        private final Datatype     dtype;
        private final Datatype     btype;

        private final long         typeSize;

        private final boolean      isArray;
        private final boolean      isEnum;
        private final boolean      isUINT64;
        private final boolean      isBitfieldOrOpaque;

        public ScalarDSDataDisplayConverter(final DataFormat theDataObject) {
            log.trace("ScalarDSDataDisplayConverter:NT={} start", NT);
            buffer = new StringBuffer();

            dtype = theDataObject.getDatatype();
            btype = dtype.getBasetype();

            typeSize = (btype == null) ? dtype.getDatatypeSize() : btype.getDatatypeSize();

            isArray = dtype.getDatatypeClass() == Datatype.CLASS_ARRAY;
            log.trace("ScalarDSDisplayConverter:isArray={} start", isArray);
            isEnum = dtype.getDatatypeClass() == Datatype.CLASS_ENUM;
            if (isArray)
                isUINT64 = (btype.isUnsigned() && (NT == 'J'));
            else
                isUINT64 = (dtype.isUnsigned() && (NT == 'J'));
            isBitfieldOrOpaque = (dtype.getDatatypeClass() == Datatype.CLASS_OPAQUE
                    || dtype.getDatatypeClass() == Datatype.CLASS_BITFIELD);
            log.trace("ScalarDSDataDisplayConverter {} finish", typeSize);
        }

        @Override
        public Object canonicalToDisplayValue(Object value) {
            if (value instanceof String) return value; // String value type doesn't need converting
            log.trace("ScalarDSDataDisplayConverter:canonicalToDisplayValue {} start", value);

            buffer.setLength(0); // clear the old string

            if (isArray) {
                int len = Array.getLength(value);
                log.trace(
                        "ScalarDSDataDisplayConverter:canonicalToDisplayValue(): isArray={} isEnum={} isBitfieldOrOpaque={} isUINT64={}",
                        isArray, isEnum, isBitfieldOrOpaque, isUINT64);

                if (showAsHex) {
                    if (isUINT64) {
                        for (int i = 0; i < len; i++) {
                            if (i > 0) buffer.append(", ");
                            buffer.append(Tools.toHexString((BigInteger) ((Object[]) value)[i], 8));
                        }
                    }
                    else {
                        for (int i = 0; i < len; i++) {
                            if (i > 0) buffer.append(", ");
                            Long l = Long.valueOf(((Object[]) value)[i].toString());
                            buffer.append(Tools.toHexString(l, (int) (typeSize / len)));
                        }
                    }
                }
                else if (showAsBin) {
                    if (isUINT64) {
                        for (int i = 0; i < len; i++) {
                            if (i > 0) buffer.append(", ");
                            buffer.append(Tools.toBinaryString((BigInteger) ((Object[]) value)[i], 8));
                        }
                    }
                    else {
                        for (int i = 0; i < len; i++) {
                            if (i > 0) buffer.append(", ");
                            Long l = Long.valueOf(((Object[]) value)[i].toString());
                            buffer.append(Tools.toBinaryString(l, (int) (typeSize / len)));
                        }
                    }
                }
                else if (isBitfieldOrOpaque) {
                    for (int i = 0; i < ((byte[]) value).length; i++) {
                        if ((i + 1) % typeSize == 0) buffer.append(", ");
                        if (i > 0) {
                            if (dtype.getDatatypeClass() == Datatype.CLASS_BITFIELD)
                                buffer.append(":");
                            else
                                buffer.append(" ");
                        }
                        buffer.append(Tools.toHexString(Long.valueOf(((byte[]) value)[i]), 1));
                    }
                }
                else if (isEnum) {
                    if (isEnumConverted) {
                        String[] outValues = new String[len];
                        String[] retValues = null;
                        long tmptid = -1;

                        try {
                            tmptid = dtype.toNative();
                            retValues = H5Datatype.convertEnumValueToName(tmptid, value, outValues);
                        }
                        catch (HDF5Exception ex) {
                            log.trace(
                                    "ScalarDSDataDisplayConverter:canonicalToDisplayValue(): Could not convert enum values to names: ex");
                            retValues = null;
                        }
                        finally {
                            try {
                                H5.H5Tclose(tmptid);
                            }
                            catch (Exception ex) {
                                log.debug(
                                        "ScalarDSDataDisplayConverter:canonicalToDisplayValue: enum H5Tclose(tmptid {}) failure: ",
                                        tmptid, ex);
                            }
                        }

                        if (retValues != null) for (int i = 0; i < outValues.length; i++) {
                            if (i > 0) buffer.append(", ");
                            buffer.append(outValues[i]);
                        }
                    }
                    else {
                        for (int i = 0; i < len; i++) {
                            if (i > 0) buffer.append(", ");
                            buffer.append(Array.get(value, i));
                        }
                    }
                }
                else {
                    // Default case if no special display type is chosen
                    for (int i = 0; i < len; i++) {
                        if (i > 0) buffer.append(", ");
                        if (isUINT64)
                            buffer.append(((Object[]) value)[i]);
                        else
                            buffer.append(((Object[]) value)[i]);
                    }
                }
            }
            else if (isEnum) {
                if (isEnumConverted) {
                    String[] outValues = new String[1];
                    String[] retValues = null;
                    long tmptid = -1;

                    try {
                        tmptid = dtype.toNative();
                        retValues = H5Datatype.convertEnumValueToName(tmptid, value, outValues);
                    }
                    catch (HDF5Exception ex) {
                        log.trace(
                                "ScalarDSDataDisplayConverter:canonicalToDisplayValue(): Could not convert enum values to names: ex");
                        retValues = null;
                    }
                    finally {
                        try {
                            H5.H5Tclose(tmptid);
                        }
                        catch (Exception ex) {
                            log.debug(
                                    "ScalarDSDataDisplayConverter:canonicalToDisplayValue: enum H5Tclose(tmptid {}) failure: ",
                                    tmptid, ex);
                        }
                    }

                    if (retValues != null) buffer.append(outValues[0]);
                }
                else
                    buffer.append(value);
            }
            else if (isBitfieldOrOpaque) {
                for (int i = 0; i < ((byte[]) value).length; i++) {
                    if (i > 0) {
                        if (dtype.getDatatypeClass() == Datatype.CLASS_BITFIELD)
                            buffer.append(":");
                        else
                            buffer.append(" ");
                    }
                    buffer.append(Tools.toHexString(Long.valueOf(((byte[]) value)[i]), 1));
                }
            }
            else {
                // Numerical values

                if (showAsHex) {
                    if (isUINT64) {
                        buffer.append(Tools.toHexString((BigInteger) value, 8));
                    }
                    else {
                        buffer.append(Tools.toHexString(Long.valueOf(value.toString()), (int) typeSize));
                    }
                }
                else if (showAsBin) {
                    if (isUINT64) {
                        buffer.append(Tools.toBinaryString((BigInteger) value, 8));
                    }
                    else {
                        buffer.append(Tools.toBinaryString(Long.valueOf(value.toString()), (int) typeSize));
                    }
                }
                else if (numberFormat != null) {
                    buffer.append(numberFormat.format(value));
                }
                else {
                    buffer.append(value.toString());
                }
            }
            log.trace("ScalarDSDataDisplayConverter:canonicalToDisplayValue {} finish", buffer);

            return buffer;
        }

        @Override
        public Object displayToCanonicalValue(Object value) {
            return value;
        }
    }

    /**
     * Update cell value label and cell value field when a cell is selected
     */
    private class ScalarDSCellSelectionListener implements ILayerListener {
        @Override
        public void handleLayerEvent(ILayerEvent e) {
            if (e instanceof CellSelectionEvent) {
                log.trace("ScalarDSCellSelectionListener: CellSelected isRegRef={} isObjRef={}", isRegRef, isObjRef);

                CellSelectionEvent event = (CellSelectionEvent) e;
                Object val = dataTable.getDataValueByPosition(event.getColumnPosition(), event.getRowPosition());
                String strVal = null;

                String[] columnNames = ((ScalarDSColumnHeaderDataProvider) columnHeaderDataProvider).columnNames;
                int rowStart = ((RowHeaderDataProvider) rowHeaderDataProvider).start;
                int rowStride = ((RowHeaderDataProvider) rowHeaderDataProvider).stride;

                cellLabel.setText(String.valueOf(
                        rowStart + indexBase + dataTable.getRowIndexByPosition(event.getRowPosition()) * rowStride)
                        + ", " + columnNames[dataTable.getColumnIndexByPosition(event.getColumnPosition())] + "  =  ");

                if (isRegRef) {
                    boolean displayValues = ViewProperties.showRegRefValues();
                    log.trace("ScalarDSCellSelectionListener: CellSelected displayValues={}", displayValues);
                    if (displayValues && val != null && ((String) val).compareTo("NULL") != 0) {
                        String reg = (String) val;
                        boolean isPointSelection = (reg.indexOf('-') <= 0);

                        // find the object location
                        String oidStr = reg.substring(reg.indexOf('/'), reg.indexOf(' '));
                        log.trace("ScalarDSCellSelectionListener: CellSelected: isPointSelection={} oidStr={}",
                                isPointSelection, oidStr);

                        // decode the region selection
                        String regStr = reg.substring(reg.indexOf('{') + 1, reg.indexOf('}'));

                        // no selection
                        if (regStr == null || regStr.length() <= 0) {
                            log.debug("ScalarDSCellSelectionListener: CellSelected: no selection made");
                            strVal = null;
                        }
                        else {
                            reg.substring(reg.indexOf('}') + 1);

                            StringTokenizer st = new StringTokenizer(regStr);
                            int nSelections = st.countTokens();
                            if (nSelections <= 0) { // no selection
                                strVal = null;
                            }
                            else {
                                log.trace("ScalarDSCellSelectionListener: CellSelected: nSelections={}", nSelections);

                                HObject obj = FileFormat.findObject(((HObject) dataObject).getFileFormat(), oidStr);
                                if (obj == null || !(obj instanceof ScalarDS)) { // no
                                    // selection
                                    strVal = null;
                                }
                                else {
                                    ScalarDS dset = (ScalarDS) obj;
                                    try {
                                        dset.init();
                                    }
                                    catch (Exception ex) {
                                        log.debug(
                                                "ScalarDSCellSelectionListener: CellSelected: reference dset did not init()",
                                                ex);
                                    }
                                    StringBuffer selectionSB = new StringBuffer();
                                    StringBuffer strvalSB = new StringBuffer();

                                    int idx = 0;
                                    while (st.hasMoreTokens()) {
                                        log.trace(
                                                "ScalarDSCellSelectionListener: CellSelected: st.hasMoreTokens() begin");

                                        int rank = dset.getRank();
                                        long start[] = dset.getStartDims();
                                        long count[] = dset.getSelectedDims();
                                        // long count[] = new long[rank];

                                        // set the selected dimension sizes
                                        // based on the region selection
                                        // info.
                                        String sizeStr = null;
                                        String token = st.nextToken();

                                        selectionSB.setLength(0);
                                        selectionSB.append(token);
                                        log.trace("ScalarDSCellSelectionListener: CellSelected: selectionSB={}",
                                                selectionSB);

                                        token = token.replace('(', ' ');
                                        token = token.replace(')', ' ');
                                        if (isPointSelection) {
                                            // point selection
                                            String[] tmp = token.split(",");
                                            for (int x = 0; x < tmp.length; x++) {
                                                count[x] = 1;
                                                sizeStr = tmp[x].trim();
                                                start[x] = Long.valueOf(sizeStr);
                                                log.trace("ScalarDSCellSelectionListener: CellSelected: point sel={}",
                                                        tmp[x]);
                                            }
                                        }
                                        else {
                                            // rectangle selection
                                            String startStr = token.substring(0, token.indexOf('-'));
                                            String endStr = token.substring(token.indexOf('-') + 1);
                                            log.trace(
                                                    "ScalarDSCellSelectionListener: CellSelected: rect sel with startStr={} endStr={}",
                                                    startStr, endStr);
                                            String[] tmp = startStr.split(",");
                                            log.trace(
                                                    "ScalarDSCellSelectionListener: CellSelected: tmp with length={} rank={}",
                                                    tmp.length,
                                                    rank);
                                            for (int x = 0; x < tmp.length; x++) {
                                                sizeStr = tmp[x].trim();
                                                start[x] = Long.valueOf(sizeStr);
                                                log.trace("ScalarDSCellSelectionListener: CellSelected: rect start={}",
                                                        tmp[x]);
                                            }
                                            tmp = endStr.split(",");
                                            for (int x = 0; x < tmp.length; x++) {
                                                sizeStr = tmp[x].trim();
                                                count[x] = Long.valueOf(sizeStr) - start[x] + 1;
                                                log.trace(
                                                        "ScalarDSCellSelectionListener: CellSelected: rect end={} count={}",
                                                        tmp[x],
                                                        count[x]);
                                            }
                                        }
                                        log.trace("ScalarDSCellSelectionListener: CellSelected: selection inited");

                                        Object dbuf = null;
                                        try {
                                            dbuf = dset.getData();
                                        }
                                        catch (Exception ex) {
                                            Tools.showError(shell, ex.getMessage(),
                                                    "Region Reference:" + shell.getText());
                                        }

                                        // Convert dbuf to a displayable
                                        // string
                                        String cName = dbuf.getClass().getName();
                                        int cIndex = cName.lastIndexOf("[");
                                        if (cIndex >= 0) {
                                            NT = cName.charAt(cIndex + 1);
                                        }
                                        log.trace("ScalarDSCellSelectionListener: CellSelected: cName={} NT={}", cName,
                                                NT);

                                        if (idx > 0) strvalSB.append(',');

                                        // convert numerical data into char
                                        // only possible cases are byte[]
                                        // and short[] (converted from
                                        // unsigned
                                        // byte)
                                        Datatype dtype = dset.getDatatype();
                                        Datatype baseType = dtype.getBasetype();
                                        log.trace("ScalarDSCellSelectionListener: CellSelected: dtype={} baseType={}",
                                                dtype.getDatatypeDescription(), baseType);
                                        if (baseType == null) baseType = dtype;
                                        if ((dtype.getDatatypeClass() == Datatype.CLASS_ARRAY
                                                && baseType.getDatatypeClass() == Datatype.CLASS_CHAR)
                                                && ((NT == 'B') || (NT == 'S'))) {
                                            int n = Array.getLength(dbuf);
                                            log.trace(
                                                    "ScalarDSCellSelectionListener: CellSelected charData length = {}",
                                                    n);
                                            char[] charData = new char[n];
                                            for (int i = 0; i < n; i++) {
                                                if (NT == 'B') {
                                                    charData[i] = (char) Array.getByte(dbuf, i);
                                                }
                                                else if (NT == 'S') {
                                                    charData[i] = (char) Array.getShort(dbuf, i);
                                                }
                                            }

                                            strvalSB.append(charData);
                                            log.trace("ScalarDSCellSelectionListener: CellSelected charData");
                                        }
                                        else {
                                            // numerical values
                                            if (dtype.getDatatypeClass() == Datatype.CLASS_ARRAY) dtype = baseType;
                                            boolean is_unsigned = dtype.isUnsigned();
                                            int n = Array.getLength(dbuf);
                                            if (is_unsigned) {
                                                switch (NT) {
                                                    case 'B':
                                                        byte[] barray = (byte[]) dbuf;
                                                        short sValue = barray[0];
                                                        if (sValue < 0) {
                                                            sValue += 256;
                                                        }
                                                        strvalSB.append(sValue);
                                                        for (int i = 1; i < n; i++) {
                                                            strvalSB.append(',');
                                                            sValue = barray[i];
                                                            if (sValue < 0) {
                                                                sValue += 256;
                                                            }
                                                            strvalSB.append(sValue);
                                                        }
                                                        break;
                                                    case 'S':
                                                        short[] sarray = (short[]) dbuf;
                                                        int iValue = sarray[0];
                                                        if (iValue < 0) {
                                                            iValue += 65536;
                                                        }
                                                        strvalSB.append(iValue);
                                                        for (int i = 1; i < n; i++) {
                                                            strvalSB.append(',');
                                                            iValue = sarray[i];
                                                            if (iValue < 0) {
                                                                iValue += 65536;
                                                            }
                                                            strvalSB.append(iValue);
                                                        }
                                                        break;
                                                    case 'I':
                                                        int[] iarray = (int[]) dbuf;
                                                        long lValue = iarray[0];
                                                        if (lValue < 0) {
                                                            lValue += 4294967296L;
                                                        }
                                                        strvalSB.append(lValue);
                                                        for (int i = 1; i < n; i++) {
                                                            strvalSB.append(',');
                                                            lValue = iarray[i];
                                                            if (lValue < 0) {
                                                                lValue += 4294967296L;
                                                            }
                                                            strvalSB.append(lValue);
                                                        }
                                                        break;
                                                    case 'J':
                                                        long[] larray = (long[]) dbuf;
                                                        Long l = larray[0];
                                                        String theValue = Long.toString(l);
                                                        if (l < 0) {
                                                            l = (l << 1) >>> 1;
                                                            BigInteger big1 = new BigInteger("9223372036854775808"); // 2^65
                                                            BigInteger big2 = new BigInteger(l.toString());
                                                            BigInteger big = big1.add(big2);
                                                            theValue = big.toString();
                                                        }
                                                        strvalSB.append(theValue);
                                                        for (int i = 1; i < n; i++) {
                                                            strvalSB.append(',');
                                                            l = larray[i];
                                                            theValue = Long.toString(l);
                                                            if (l < 0) {
                                                                l = (l << 1) >>> 1;
                                                                BigInteger big1 = new BigInteger("9223372036854775808"); // 2^65
                                                                BigInteger big2 = new BigInteger(l.toString());
                                                                BigInteger big = big1.add(big2);
                                                                theValue = big.toString();
                                                            }
                                                            strvalSB.append(theValue);
                                                        }
                                                        break;
                                                    default:
                                                        strvalSB.append(Array.get(dbuf, 0));
                                                        for (int i = 1; i < n; i++) {
                                                            strvalSB.append(',');
                                                            strvalSB.append(Array.get(dbuf, i));
                                                        }
                                                        break;
                                                }
                                            }
                                            else {
                                                for (int x = 0; x < n; x++) {
                                                    Object theValue = Array.get(dbuf, x);
                                                    if (x > 0) strvalSB.append(',');
                                                    strvalSB.append(theValue);
                                                }
                                            }
                                            log.trace("ScalarDSCellSelectionListener: CellSelected: byteString");
                                        }
                                        idx++;
                                        dset.clearData();
                                        log.trace(
                                                "ScalarDSCellSelectionListener: CellSelected: st.hasMoreTokens() end");
                                    } // while (st.hasMoreTokens())
                                    strVal = strvalSB.toString();
                                    log.trace("ScalarDSCellSelectionListener: CellSelected: st.hasMoreTokens() end");
                                }
                            }
                        }
                    }
                    else {
                        strVal = null;
                    }
                }
                else if (isObjRef) {
                    Long ref = (Long) val;
                    long oid[] = { ref.longValue() };

                    // decode object ID
                    try {
                        HObject obj = FileFormat.findObject(((HObject) dataObject).getFileFormat(), oid);
                        strVal = obj.getFullName();
                    }
                    catch (Exception ex) {
                        strVal = null;
                    }
                }

                if (strVal == null && val != null)
                    strVal = dataDisplayConverter.canonicalToDisplayValue(val).toString();

                cellValueField.setText(strVal);

                log.trace("ScalarDSCellSelectionListener: CellSelected finish");
            }
        }
    }

    /**
     * Custom Column Header data provider to set column indices based on Index Base
     * for Scalar Datasets.
     */
    private class ScalarDSColumnHeaderDataProvider implements IDataProvider {

        private final String columnNames[];

        private final int    rank;

        private final long[] startArray;
        private final long[] strideArray;
        private final int[]  selectedIndex;

        private final int    ncols;

        public ScalarDSColumnHeaderDataProvider(DataFormat theDataObject) {
            rank = theDataObject.getRank();

            startArray = theDataObject.getStartDims();
            strideArray = theDataObject.getStride();
            selectedIndex = theDataObject.getSelectedIndex();

            if (rank > 1) {
                ncols = (int) theDataObject.getWidth();

                int start = (int) startArray[selectedIndex[1]];
                int stride = (int) strideArray[selectedIndex[1]];

                columnNames = new String[ncols];

                for (int i = 0; i < ncols; i++) {
                    columnNames[i] = String.valueOf(start + indexBase + i * stride);
                }
            }
            else {
                ncols = 1;

                columnNames = new String[] { "  " };
            }
        }

        @Override
        public int getColumnCount() {
            return ncols;
        }

        @Override
        public int getRowCount() {
            return 1;
        }

        @Override
        public Object getDataValue(int columnIndex, int rowIndex) {
            return columnNames[columnIndex];
        }

        @Override
        public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
            return;
        }
    }
}
