import java.util.ArrayList;
import java.util.List;

// 数据结构都定义在这里, 之后可能找个别的地方放.
public class Main {

    public static void main(String[] args) {
	// write your code here
    }
}

class Attribute {
    public String name;
    public int type;
    public int length;
    public boolean isPrimaryKey;
    public boolean isUnique;
    public String index;

    public Attribute() {
        name = null;
        isPrimaryKey = false;
        isUnique = false;
        index = "";
    }

    public Attribute(String n, int t, boolean isPr, boolean isUn, String idx) {
        name = n;
        type = t;
        isPrimaryKey = isPr;
        isUnique = isUn;
        index = idx;
        switch (type) {
            case -1:
            case 0:
                length = 4;
                break;
            default:
                length = type;
                break;
        }
    }
}

class Table {
    public String name;
    int blockNum;
    int attrNum;
    int totalLength;
    int nextInsertBlock;
    List<Attribute> attributes = new ArrayList<>();

    public Table() {
        blockNum = 0;
        attrNum = 0;
        totalLength = 0;
        nextInsertBlock = 0;
    }

    public Table(String name, List<Attribute> attributes) {
        this.name = name;
        this.attributes = attributes;
        blockNum = 0;
        attrNum = attributes.size();
        nextInsertBlock = 0;
        totalLength = 0;
        for (Attribute attr : attributes) {
            if (attr.type <= 0)
                totalLength += 4;
            else
                totalLength += attr.type;
        }
    }

    public Table(String name, int blockNum, int attrNum, int totalLength,
                 int nextInsertBlock, List<Attribute> attributes) {
        this.name = name;
        this.blockNum = blockNum;
        this.attrNum = attrNum;
        this.totalLength = totalLength;
        this.nextInsertBlock = nextInsertBlock;
        this.attributes = attributes;
    }
}

class Index {
    public String indexName;
    public String tableName;

    // 索引属性在 Table.attributes 中的序号.
    public int columnIndex;
    public int columnLength;
    int blockNum;
    int rootBlockOffset;

    public Index() {}

    public Index(String indexName, String tableName) {
        this.indexName = indexName;
        this.tableName = tableName;
        columnIndex = 0;
        columnLength = 0;
        blockNum = 0;
        rootBlockOffset = 0;
    }

    public Index(String indexName, String tableName, int columnIndex,
                 int columnLength, int blockNum, int rootBlockOffset) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnIndex = columnIndex;
        this.columnLength = columnLength;
        this.blockNum = blockNum;
        this.rootBlockOffset = rootBlockOffset;
    }
}

class Data {
    public ArrayList<Row> rows;

    public Data() {
        rows = new ArrayList<Row>();
    }

    public void add(Row row) {
        rows.add(row);
    }
}

class Row {
    public String[] columns;

    public Row() {}

    public Row(String[] columns) {
        this.columns = columns;
    }
}

class SyntaxException extends Exception {}
class TableExistedException extends Exception {}
class IndexExistedException extends Exception {}
class TableNotFoundException extends Exception {}
class AttributeNotFoundException extends Exception {}
