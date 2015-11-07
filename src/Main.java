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
    public int pos;
    public int columnLength;
    int blockNum;
    int rootBlockOffset;
    int type;

    public Index() {}

    public Index(String indexName, String tableName, int type) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.type = type;
        columnIndex = 0;
        pos = 0;
        columnLength = 0;
        blockNum = 0;
        rootBlockOffset = 0;
    }

    public Index(String indexName, String tableName, int columnIndex,
                 int pos, int columnLength, int blockNum,
                 int rootBlockOffset, int type) {
        this.indexName = indexName;
        this.tableName = tableName;
        this.columnIndex = columnIndex;
        this.pos = pos;
        this.columnLength = columnLength;
        this.blockNum = blockNum;
        this.rootBlockOffset = rootBlockOffset;
        this.type = type;
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

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}

class Row {
    public String[] columns;

    public Row() {}

    public Row(String[] columns) {
        this.columns = columns;
    }
}

interface DetailedErrorMessage {
    public void DetailedErrorMessage();
}

class SQLException extends Exception implements DetailedErrorMessage {
    public void DetailedErrorMessage() {};
};

class SyntaxException extends SQLException {}

class TableExistedException extends SQLException {
    private String tableName;

    public TableExistedException(String tableName) {
        this.tableName = tableName;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: table existed while creating table");
        System.out.println("Table name: " + tableName);
    }
}

class IndexExistedException extends SQLException {
    private String indexName;

    public IndexExistedException(String indexName) {
        this.indexName = indexName;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: index existed while creating index");
        System.out.println("Index name: " + indexName);
    }

}

class TableNotFoundException extends SQLException {
    private String tableName;

    public TableNotFoundException(String tableName) {
        this.tableName = tableName;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: table not found");
        System.out.println("Table name: " + tableName);
    }
}

class AttributeNotFoundException extends SQLException {
    private String attrName;

    public AttributeNotFoundException(String attrName) {
        this.attrName = attrName;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: attribute not found");
        System.out.println("Attribute name: " + attrName);
    }
}

class UniqueKeyException extends SQLException {
    private String indexName;

    public UniqueKeyException(String indexName) {
        this.indexName = indexName;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: unique key error");
        System.out.println("Index name: " + indexName);
    }
}
class AttributeNumberException extends SQLException {
    private String tableName;
    private int expectedSize, actualSize;

    public AttributeNumberException(String tableName, int expectedSize, int actualSize) {
        this.tableName = tableName;
        this.expectedSize = expectedSize;
        this.actualSize = actualSize;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: attribute number doesn't match");
        System.out.println("On table: " + tableName +
                           " Expected number: " + expectedSize +
                           " Actual number: " + actualSize);
    }
}
class AttributeFormatException extends SQLException {
    private String attrName, type, value;

    public AttributeFormatException(String attrName, int type, String value) {
        this.attrName = attrName;
        if (type == -1)
            this.type = "int";
        else if (type == 0)
            this.type = "float";
        else
            this.type = "char(" + type + ")";
        this.value = value;
    }

    public void DetailedErrorMessage() {
        System.out.println("Error: attribute format doesn't match");
        System.out.println("On attribute: " + attrName +
                           " with type of " + type +
                           " get value " + value);
    }
}
