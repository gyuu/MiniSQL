import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

public class RecordManager {
    private static final int HEADER_SIZE = 4;
    private static final int POINTER_SIZE = 3;
    private static final short END_OF_BLOCK = 32767;
    private static final short NEXT_RECORD = 0;
    private static final int NOT_EMPTY = 1;
    private static final int EMPTY = 0;

    private BufferManager bm;
    private CatalogManager cm;
    private IndexManager im;

    public void setBMCMIM(BufferManager bm, CatalogManager cm, IndexManager im) {
        this.bm = bm;
        this.cm = cm;
        this.im = im;
    }

    /*
    ** Blocks use the free list structure.
    **
    ** The header record contains 4 bytes, in which:
    ** the first 2 bytes point to the first available record for insertion, and
    ** the next 2 bytes is the total number of records in the block.
    **
    ** Followed are record bytes.
    ** A record starts with 1 byte indicating whether it is used (0 or 1),
    ** and ends with 2 bytes that point to next available record.
    ** If a record is not used (or deleted), it points to the next available
    ** record for insertion.
    **
    ** Records are indexed from 0 to the capacity of the block.
    ** If the next pointer of a record is 0 (NEXT_RECORD), its next available
    ** record is exactly the next record bytes on the disk.
    */
    private int getInsertIndex(byte[] block) {
        return ((block[0] & 0xFF) << 8) + (block[1] & 0xFF);
    }

    private int getNextInsertIndex(byte[] block, Table table, int recordIndex) {
        int offset = getPositionFromIndex(table, recordIndex) + table.totalLength + 1;
        int nextIndex = ((block[offset] & 0xFF) << 8) + (block[offset + 1] & 0xFF);
        if (nextIndex == NEXT_RECORD)
            return recordIndex + 1;
        else
            return nextIndex;
    }

    private void setInsertIndex(byte[] block, int insertIndex) {
        block[0] = (byte) (insertIndex >> 8);
        block[1] = (byte) insertIndex;
    }

    private void setNextInsertIndex(byte[] block, Table table, int recordIndex, int nextIndex) {
        if (recordIndex == -1)    // set the insert index for the whole block
            setInsertIndex(block, nextIndex);
        else {
            int offset = getPositionFromIndex(table, recordIndex) + table.totalLength + 1;
            block[offset] = (byte) (nextIndex >> 8);
            block[offset + 1] = (byte) nextIndex;
        }
    }

    private int getPositionFromIndex(Table table, int recordIndex) {
        // (table.totalLength + HEADER_SIZE) is the size of per record.
        return HEADER_SIZE + recordIndex * (table.totalLength + POINTER_SIZE);
    }

    private int getRecordNum(byte[] block) {
        return ((block[2] & 0xFF) << 8) + (block[3] & 0xFF);
    }

    private void incRecordNum(byte[] block) {    // increase record number by 1
        int recordNum = getRecordNum(block);
        recordNum++;
        block[2] = (byte) (recordNum >> 8);
        block[3] = (byte) recordNum;
    }

    private void decRecordNum(byte[] block) {    // decrease record number by 1
        int recordNum = getRecordNum(block);
        recordNum--;
        block[2] = (byte) (recordNum >> 8);
        block[3] = (byte) recordNum;
    }

    private final byte[] getColumnBytes(String column, Attribute attr)
        throws AttributeFormatException {
        if (attr.type == -1) {   // int
            try {
                int value = Integer.parseInt(column);
                return ByteBuffer.allocate(4).putInt(value).array();
            }
            catch (NumberFormatException e) {
                throw new AttributeFormatException();
            }
        }
        else if (attr.type == 0) {    // float
            try {
                float value = Float.parseFloat(column);
                return ByteBuffer.allocate(4).putFloat(value).array();
            }
            catch (NumberFormatException e) {
                throw new AttributeFormatException();
            }
        }
        else {    // char
            if (!column.startsWith("'") || !column.endsWith("'"))
                throw new AttributeFormatException();
            column = column.replaceAll("'", "");
            return column.getBytes();
        }
    }

    private final byte[] getInsertBytes(Table table, List<String> row)
        throws AttributeFormatException, AttributeNumberException {
        if (row.size() != table.attributes.size())
            throw new AttributeNumberException();
        byte[] bytesToInsert = new byte[table.totalLength];
        int index = 0;
        for (int i = 0; i < table.attributes.size(); i++) {
            byte[] columnBytes = getColumnBytes(row.get(i), table.attributes.get(i));
            System.arraycopy(columnBytes, 0, bytesToInsert, index, columnBytes.length);
            index += columnBytes.length;
        }
        return bytesToInsert;
    }

    public void insertRecord(String tableName, List<String> row)
        throws UniqueKeyException, Exception {
        Table table = cm.getTable(tableName);
        byte[] bytesToInsert = getInsertBytes(table, row);

        // check uniqueness
        ArrayList<Index> allTableIndices = cm.getAllIndicesOfTable(tableName);
        for (Index idx : allTableIndices) {
            if (im.searchEqual(idx, bytesToInsert) != null)
                throw new UniqueKeyException();
        }

        // Use free list for insertion and deletion
        int recordSize = table.totalLength + POINTER_SIZE;

        while (true) {
            BufferNode bn = bm.getIfIsInBuffer(table.name + ".table", table.nextInsertBlock);
            if (bn == null)
                throw new RuntimeException();
            byte[] block = bn.data;
            int insertIndex = getInsertIndex(block);
            int pos = getPositionFromIndex(table, insertIndex);

            // No free space, get a new block
            if (pos + recordSize > BufferManager.BLOCK_SIZE) {
                table.nextInsertBlock++;
                if (table.nextInsertBlock >= table.blockNum)
                    bm.addBlockInFile(table);
                continue;
            }

            // Write to buffer
            block[pos] = NOT_EMPTY;
            System.arraycopy(bytesToInsert, 0, block, pos + 1, table.totalLength);

            // Modify available insert index value and increase record number
            int nextIndex = getNextInsertIndex(block, table, insertIndex);
            setInsertIndex(block, nextIndex);
            incRecordNum(block);

            // Update index
            for (Attribute attr : table.attributes) {
                if (!attr.index.equals("")) {    // has index
                    Index idx = cm.getIndex(attr.index);
                    im.insertKey(idx, bytesToInsert, table.nextInsertBlock, pos);
                }
            }

            bn.isWritten = true;
            return;
        }
    }

    private boolean matchAllCond(Table table, byte[] bytes, List<Condition> conditions) {
        for (Condition cond : conditions)
            if (!matchCondition(table, bytes, cond))
                // don't match any one of conditions
                return false;

        return true;
    }

    private boolean matchCondition(Table table, byte[] bytes, Condition cond) {
        int columnStart = 0;
        for (Attribute attr : table.attributes) {
            if (cond.attribute_name.equals(attr.name)) {
                if (attr.type == -1) {
                    byte[] column = Arrays.copyOfRange(bytes, columnStart, columnStart + 4);
                    int value = ByteBuffer.wrap(column).getInt();
                    return cond.if_right(value);
                }
                else if (attr.type == 0) {
                    byte[] column = Arrays.copyOfRange(bytes, columnStart, columnStart + 4);
                    float value = ByteBuffer.wrap(column).getFloat();
                    return cond.if_right(value);
                }
                else {
                    byte[] column = Arrays.copyOfRange(bytes, columnStart,
                                                       columnStart + attr.length);
                    String value = new String(column);
                    return cond.if_right(value);
                }
            }
            else
                columnStart += attr.length;
        }
        return false;
    }

    public int deleteRecord(String tableName)
        throws Exception {    // delete all
        ArrayList<Condition> emptyCond = new ArrayList<Condition>();    // Always returns true
        return deleteRecord(tableName, emptyCond);
    }

    public int deleteRecord(String tableName, List<Condition> conditions)
        throws Exception {
        Table table = cm.getTable(tableName);
        int count = 0;

        for (int blockOffset = 0; blockOffset < table.blockNum; blockOffset++) {
            BufferNode bn = bm.getBufferNode(table.name + ".table", blockOffset);
            byte[] block = bn.data;
            int recordNum = getRecordNum(block);
            int recordIndex = 0;
            int accessedRecordNum = 0;
            int nextDeleted = getInsertIndex(block);
            int prevDeleted = -1;

            ArrayList<Index> allTableIndices = cm.getAllIndicesOfTable(tableName);

            while (accessedRecordNum < recordNum) {
                int pos = getPositionFromIndex(table, recordIndex);
                if (block[pos] == EMPTY) {    // record is empty, skip
                    recordIndex++;
                    continue;
                }

                byte[] recordBytes = bn.getBytes(pos + 1, table.totalLength);
                if (matchAllCond(table, recordBytes, conditions)) {
                    block[pos] = EMPTY;
                    if (recordIndex < nextDeleted) {
                        setNextInsertIndex(block, table, prevDeleted, recordIndex);
                        setNextInsertIndex(block, table, recordIndex, nextDeleted);
                        prevDeleted = recordIndex;
                    }
                    else {
                        int nextOfNext = getNextInsertIndex(block, table, nextDeleted);
                        setNextInsertIndex(block, table, nextDeleted, recordIndex);
                        setNextInsertIndex(block, table, recordIndex, nextOfNext);
                        nextDeleted = nextOfNext;
                        prevDeleted = recordIndex;
                    }

                    decRecordNum(block);
                    // there remains some space for insertion
                    if (table.nextInsertBlock > blockOffset)
                        table.nextInsertBlock = blockOffset;

                    // Delete in index
                    for (Index idx : allTableIndices) {
                        im.deleteKey(idx, recordBytes);
                    }

                    bn.isWritten = true;
                    count++;
                }
                recordIndex++;
                accessedRecordNum++;
            }
        }
        return count;
    }

    private String[] bytesToString(Table table, byte[] bytes) {
        int columnStart = 0;
        String[] result = new String[table.attrNum];
        int attrRead = 0;

        for (Attribute attr : table.attributes) {
            if (attr.type == -1) {
                byte[] column = Arrays.copyOfRange(bytes, columnStart, columnStart + 4);
                int value = ByteBuffer.wrap(column).getInt();
                result[attrRead++] = String.valueOf(value);
            }
            else if (attr.type == 0) {
                byte[] column = Arrays.copyOfRange(bytes, columnStart, columnStart + 4);
                float value = ByteBuffer.wrap(column).getFloat();
                result[attrRead++] = String.valueOf(value);
            }
            else {
                byte[] column = Arrays.copyOfRange(bytes, columnStart, columnStart + attr.length);
                result[attrRead++] = new String(column);
            }
            columnStart += attr.length;
        }

        return result;
    }

    public Data selectRecord(String tableName) {
        ArrayList<Condition> emptyCond = new ArrayList<Condition>();    // Always returns true
        return selectRecord(tableName, emptyCond);
    }

    public Data selectRecord(String tableName, ArrayList<Condition> conditions) {
        Table table = cm.getTable(tableName);
        Data selectResult = new Data();

        for (int blockOffset = 0; blockOffset < table.blockNum; blockOffset++) {
            BufferNode bn = bm.getBufferNode(table.name + ".table", blockOffset);
            byte[] block = bn.data;
            int recordNum = getRecordNum(block);
            int recordIndex = 0;
            int accessedRecordNum = 0;

            while (accessedRecordNum < recordNum) {
                int pos = getPositionFromIndex(table, recordIndex);
                if (block[pos] == EMPTY) {    // record is empty, skip
                    recordIndex++;
                    continue;
                }

                byte[] recordBytes = bn.getBytes(pos + 1, table.totalLength);
                if (matchAllCond(table, recordBytes, conditions)) {
                    byte[] bytes = bn.getBytes(pos + 1, table.totalLength);
                    selectResult.add(new Row(bytesToString(table, bytes)));
                }
                recordIndex++;
                accessedRecordNum++;
            }
        }

        return selectResult;
    }

    public void dropTable(Table table) {
        String filename = table.name + ".table";
        try {
            File f = new File(filename);
            f.delete();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createTable(Table table) {
        String filename = table.name + ".table";
        try {
            File f = new File(filename);
            f.createNewFile();
            bm.addBlockInFile(table);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        /*
        ArrayList<Attribute> attrs = new ArrayList<Attribute>();
        attrs.add(new Attribute("id", -1, false, false));
        attrs.add(new Attribute("name", 5, false, false));
        attrs.add(new Attribute("salary", 0, false, false));
        Table table = new Table("Person", attrs);

        RecordManager rm = new RecordManager();

        rm.createTable(table);

        for (int i = 0; i < 258; i++) {
            ArrayList<String> columns = new ArrayList<String>();
            columns.add("" + i);
            columns.add("Alice");
            columns.add("25.4");
            rm.insertRecord(table, columns);
        }

        Condition c = new Condition("id", "4", 0);
        ArrayList<Condition> cs = new ArrayList<Condition>();
        cs.add(c);

        /*
        Data data = rm.selectRecord(table, cs);
        for (Row row : data.rows) {
            for (String column : row.columns) {
                System.out.print(column + " ");
            }
            System.out.println("");
        }
        */

        //rm.bm.WriteAllToFile();

    }
}
