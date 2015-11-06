import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileOutputStream;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class CatalogManager {
    private int tableNum = 0;
    private int indexNum = 0;
    private HashMap<String, Table> tables = new HashMap<String, Table>();
    private HashMap<String, Index> indices = new HashMap<String, Index>();

    private IndexManager im;
    private RecordManager rm;

    public void setIMRM(IndexManager im, RecordManager rm) {
        this.im = im;
        this.rm = rm;
    }

    public CatalogManager() {
        // Load table catalog from table.json
        JSONParser parser = new JSONParser();
        try {
            JSONArray tableCatalog = (JSONArray) parser.parse(new FileReader("table.json"));
            for (Object table_o : tableCatalog) {
                JSONObject table = (JSONObject) table_o;
                String tableName = (String) table.get("name");
                int blockNum = (int)(long) table.get("blockNum");
                int attrNum = (int)(long) table.get("attrNum");
                int totalLength = (int)(long) table.get("totalLength");
                int nextInsertBlock = (int)(long) table.get("nextInsertBlock");
                JSONArray attrsArray = (JSONArray) table.get("attributes");

                ArrayList<Attribute> attrs = new ArrayList<Attribute>();
                for (Object attr_o : attrsArray) {
                    JSONObject attr = (JSONObject) attr_o;
                    String attrName = (String) attr.get("name");
                    int type = (int)(long) attr.get("type");
                    int length = (int)(long) attr.get("length");
                    boolean isPrimaryKey = (boolean) attr.get("isPrimaryKey");
                    boolean isUnique = (boolean) attr.get("isUnique");
                    String index = (String) attr.get("index");
                    attrs.add(new Attribute(attrName, type, isPrimaryKey, isUnique, index));
                }

                createTable(tableName, blockNum, attrNum, totalLength, nextInsertBlock, attrs);
            }
        }
        catch (FileNotFoundException e) {}    // if table.json doesn't exist, ignore the exception
        catch (Exception e) {
            e.printStackTrace();
        }

        // Load index catalog from index.json
        try {
            JSONArray indexCatalog = (JSONArray) parser.parse(new FileReader("index.json"));
            for (Object index_o : indexCatalog) {
                JSONObject index = (JSONObject) index_o;
                String indexName = (String) index.get("indexName");
                String tableName = (String) index.get("tableName");
                int columnIndex = (int)(long) index.get("columnIndex");
                int pos = (int)(long) index.get("pos");
                int columnLength = (int)(long) index.get("columnLength");
                int blockNum = (int)(long) index.get("blockNum");
                int rootBlockOffset = (int)(long) index.get("rootBlockOffset");
                int type = (int)(long) index.get("type");

                createIndex(indexName, tableName, columnIndex, pos,
                            columnLength, blockNum, rootBlockOffset, type);
            }
        }
        catch (FileNotFoundException e) {}    // if index.json doesn't exist, ignore the exception
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        // Save table catalog
        JSONArray tableCatalog = new JSONArray();
        for (Table table : tables.values()) {
            JSONObject tableObj = new JSONObject();

            tableObj.put("name", table.name);
            tableObj.put("blockNum", table.blockNum);
            tableObj.put("attrNum", table.attrNum);
            tableObj.put("totalLength", table.totalLength);
            tableObj.put("nextInsertBlock", table.nextInsertBlock);

            JSONArray attrs = new JSONArray();
            for (Attribute attr : table.attributes) {
                JSONObject attrObj = new JSONObject();
                attrObj.put("name", attr.name);
                attrObj.put("type", attr.type);
                attrObj.put("length", attr.length);
                attrObj.put("isPrimaryKey", attr.isPrimaryKey);
                attrObj.put("isUnique", attr.isUnique);
                attrObj.put("index", attr.index);
                attrs.add(attrObj);
            }
            tableObj.put("attributes", attrs);

            tableCatalog.add(tableObj);
        }
        try (FileWriter file = new FileWriter("table.json")) {
            file.write(tableCatalog.toJSONString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // Save index catalog
        JSONArray indexCatalog = new JSONArray();
        for (Index index : indices.values()) {
            JSONObject indexObj = new JSONObject();
            indexObj.put("indexName", index.indexName);
            indexObj.put("tableName", index.tableName);
            indexObj.put("columnIndex", index.columnIndex);
            indexObj.put("pos", index.pos);
            indexObj.put("columnLength", index.columnLength);
            indexObj.put("blockNum", index.blockNum);
            indexObj.put("rootBlockOffset", index.rootBlockOffset);
            indexObj.put("type", index.type);
            indexCatalog.add(indexObj);
        }
        try (FileWriter file = new FileWriter("index.json")) {
            file.write(indexCatalog.toJSONString());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createTable(String tableName, ArrayList<Attribute> attrs)
        throws TableExistedException, IndexExistedException, IOException,
               AttributeNotFoundException, TableNotFoundException {
        if (tableExisted(tableName))
            throw new TableExistedException();
        Table t = new Table(tableName, attrs);
        rm.createTable(t);

        tables.put(tableName, t);
        tableNum++;

        // Create index for primary keys and unique keys
        for (Attribute attr : attrs) {
            if (attr.isUnique || attr.isPrimaryKey) {
                String implicitIndexName = tableName + "_" + attr.name;
                createIndex(implicitIndexName, tableName, attr.name);
            }
        }
    }

    public void createTable(String tableName, int blockNum, int attrNum, int totalLength,
                            int nextInsertBlock, ArrayList<Attribute> attrs) {
        Table table = new Table(tableName, blockNum, attrNum,
                                totalLength, nextInsertBlock, attrs);
        tables.put(tableName, table);
        tableNum++;
    }

    public void createIndex(String indexName, String tableName, String attribute_name)
        throws IndexExistedException, IOException, AttributeNotFoundException,
               TableNotFoundException {
        if (indexExisted(indexName))
            throw new IndexExistedException();
        Index index = new Index(indexName, tableName, -1);
        Table table = getTable(tableName);
        if (table == null)
            throw new TableNotFoundException();

        int pos = 0;
        for (int i = 0; i < table.attributes.size(); i++) {
            Attribute attr = table.attributes.get(i);
            if (attr.name.equals(attribute_name)) {
                index.columnIndex = i;
                index.pos = pos;
                index.columnLength = attr.length;
                index.type = attr.type;
                attr.index = indexName;
                im.createIndex(table, index);
                indices.put(indexName, index);
                indexNum++;
                return;
            }
            pos += attr.length;
        }
        throw new AttributeNotFoundException();
    }

    public void createIndex(String indexName, String tableName, int columnIndex,
                             int pos, int columnLength, int blockNum,
                             int rootBlockOffset, int type) {
        Index index = new Index(indexName, tableName, columnIndex, pos,
                                columnLength, blockNum, rootBlockOffset, type);
        indices.put(indexName, index);
        indexNum++;
    }

    public void dropTable(String tableName)
        throws TableNotFoundException {
        if (!tableExisted(tableName)) {
            throw new TableNotFoundException();
        }

        Table table = getTable(tableName);
        rm.dropTable(table);
        tables.remove(tableName);
        tableNum--;

        Iterator it = indices.values().iterator();
        while (it.hasNext()) {    // Remove all indices that belongs to the table
            Index index = (Index) it.next();
            if (index.tableName.equals(tableName)) {
                im.dropIndex(index.indexName);
                it.remove();
            }
        }
    }

    public void dropIndex(String indexName) {
        if (indexExisted(indexName)) {
            im.dropIndex(indexName);
            indices.remove(indexName);
            indexNum--;
        }
    }

    private boolean indexExisted(String indexName) {
        return indices.containsKey(indexName);
    }

    private boolean tableExisted(String tableName) {
        return tables.containsKey(tableName);
    }

    public Table getTable(String tableName) {
        if (tableExisted(tableName))
            return tables.get(tableName);
        else
            return null;
    }

    public Index getIndex(String indexName) {
        if (indexExisted(indexName))
            return indices.get(indexName);
        else
            return null;
    }

    public ArrayList<Index> getAllIndicesOfTable(String tableName) {
            // Get all indices in the table
        if (tableExisted(tableName)) {
            Table table = getTable(tableName);
            ArrayList<Index> allTableIndices = new ArrayList<Index>();
            for (Attribute attr : table.attributes) {
                if (!attr.index.equals("")) {    // has index
                    allTableIndices.add(getIndex(attr.index));
                }
            }
            return allTableIndices;
        }
        else
            return null;
    }

    public static void main(String[] args) {
        /*
        ArrayList<Attribute> attrs = new ArrayList<Attribute>();
        attrs.add(new Attribute("id", -1, false, false));
        attrs.add(new Attribute("name", 5, false, false));
        attrs.add(new Attribute("salary", 0, false, false));

        CatalogManager cm = new CatalogManager();
        cm.createTable("person", attrs);
        cm.createTable("test", attrs);
        cm.createIndex("person", "id");
        cm.close();

        CatalogManager cm2 = new CatalogManager();
        System.out.println(cm2.tables);
        System.out.println(cm2.indices);
        */
    }
}
