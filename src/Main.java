import java.util.LinkedList;
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
    public boolean unique;
    public String index;

    public Attribute() {
        name = null;
        isPrimaryKey = false;
        unique = false;
        index = "";
    }

    public Attribute(String n, int t, int l, boolean isPr, boolean isUn) {
        name = n;
        type = t;
        length = l;
        isPrimaryKey = isPr;
        unique = isUn;
    }
}

class Table {
    public String name;
    int blockNum;
    int attrNum;
    int totalLength;
    List<Attribute> attributes = new LinkedList<>();
}

class Index {
    public String indexName;
    public String tableName;

    // 索引属性在 Table.attributes 中的序号.
    int columnIndex;
    int columnLength;
    int blockNum;
}

class Row {
    public List<String> columns;
}

class Data {
    public List<Row> rows;
}


class SyntaxException extends Exception {

}