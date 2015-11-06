import java.io.*;

/**
 * Created by gyuu on 15/11/5.
 */

public class IndexManager{

    public static BufferManager  buf;

    IndexManager(BufferManager buffer){
        buf=buffer;
    }

    //找出需要插入的索引值
    private static byte[] getColumnValue(Table  tableInfo, Index  indexInfo, byte[] row){

        int s_pos = 0, f_pos = 0;
        for(int i= 0; i <= indexInfo.columnIndex; i++){
            s_pos = f_pos;
            //f_pos += tableinfor.attributes.get(i).length; //找出记录中第indexinfo。column列的长度为该列属性长度的字符串
            f_pos+=tableInfo.attributes.get(i).length;
        }
        byte[] colValue=new byte[f_pos-s_pos];
        for(int j=0;j<f_pos-s_pos;j++){//返回该子字符串，即为需要插入的索引字符串
            colValue[j]=row[s_pos+j];
        }
        return colValue;
    }

    //创建索引
    public void createIndex(Table tableInfo,Index indexInfo) throws IOException { //需要API提供表和索引信息结构

        BPlusTree thisTree=new BPlusTree(indexInfo, buf); //创建一棵新树

        //开始正式建立索引
        String filename=tableInfo.name+".table";
        try{
            for(int blockOffset=0; blockOffset< tableInfo.blockNum; blockOffset++){
                BufferNode block = buf.getBufferNode(filename, blockOffset);

                int recordNum = 0; // 每个块的头两个字节存储该块中的记录数量(不包括被删除的).
                recordNum += (block.data[2] & 0xFF) << 8;
                recordNum += (block.data[3] & 0xFF);

                for(int offset =0; offset < recordNum /*tableInfo.maxPerRecordNum*/; offset++){
                    // 每条记录存储时,数据前面留 1 个字节,后面附加 2 个字节有其他用处.因此每条记录实际占 totalLength+3 字节.
                    int position = 4 + offset * (tableInfo.totalLength + 3);
                    // 每条记录第 1 个个字节如果为 0, 表示已被删除, 如果为 1, 表示可用数据.
                    int notDeleted = (block.data[position] & 0xFF);
                    if (notDeleted == 0)
                        continue;
                    byte[] Record = block.getBytes(position+1, tableInfo.totalLength); //读取表中的每条记录
                    byte[] key= getColumnValue(tableInfo,indexInfo,Record); //找出索引值
                    thisTree.insert(key, blockOffset, offset); //插入树中
                }
            }
        }catch(NullPointerException e){
            System.err.println("must not be null for key.");
        }
        catch(Exception e){
            System.err.println("the index has not been created.");
            System.err.println(e);
        }

        indexInfo.rootBlockOffset=thisTree.myRootBlock.blockOffset;
//        CatalogManager.setIndexRoot(indexInfo.indexName, thisTree.myRootBlock.blockOffset);

        buf.WriteAllToFile();

        System.out.println("创建索引成功！");
    }

    //删除索引，即删除索引文件
    public void dropIndex(String filename ){
        filename+=".index";
        File file = new File(filename);

        try{
            if(file.exists())
                if(file.delete())
                    System.out.println("索引文件已删除");
                else
                    System.out.println("文件"+filename+"没有找到");
        }catch(Exception   e){
            System.out.println(e.getMessage());
            System.out.println("删除索引失败！");
        }

        buf.setInvalid(filename);  //将buf中所有与此索引相关的缓冲块都置为无效

        System.out.println("删除索引成功！");
    }

    //等值查找
    public offsetInfo searchEqual(Index indexInfo, byte[] key) throws Exception{
        offsetInfo off;
        try{
            //Index inx=CatalogManager.getIndex(indexInfo.indexName);
            BPlusTree thisTree=new BPlusTree(indexInfo,buf, indexInfo.rootBlockOffset); //创建树访问结构（但不是新树）
            off=thisTree.searchKey(key);  //找到位置信息体，返回给API
            return off;
        }catch(NullPointerException e){
            System.err.println();
            return null;
        }
    }

    /*
        public List<offsetInfo> searchBetween(Index indexInfo, String firstKey,String lastKey){
            List<offsetInfo> offlist=null;
            return null;
        }
    */
    //插入新索引值，已有索引则更新位置信息
    public void insertKey(Index indexInfo,byte[] key,int blockOffset,int offset) throws Exception{
        try{
            //Index inx=CatalogManager.getIndex(indexInfo.indexName);
            BPlusTree thisTree=new BPlusTree(indexInfo,buf,indexInfo.rootBlockOffset);//创建树访问结构（但不是新树）
            thisTree.insert(key, blockOffset, offset);	//插入
            indexInfo.rootBlockOffset=thisTree.myRootBlock.blockOffset;//设置根块
//            CatalogManager.setIndexRoot(indexInfo.indexName, thisTree.myRootBlock.blockOffset);
        }catch(NullPointerException e){
            System.err.println();
        }

    }

    //删除索引值，没有该索引则什么也不做
    public void deleteKey(Index indexInfo,byte[] deleteKey) throws Exception{
        try{
            //Index inx=CatalogManager.getIndex(indexInfo.indexName);
            BPlusTree thisTree=new BPlusTree(indexInfo,buf,indexInfo.rootBlockOffset);//创建树访问结构（但不是新树）
            thisTree.delete(deleteKey);	//删除
            indexInfo.rootBlockOffset=thisTree.myRootBlock.blockOffset;//设置根块
//            CatalogManager.setIndexRoot(indexInfo.indexName, thisTree.myRootBlock.blockOffset);
        }catch(NullPointerException e){
            System.err.println();
        }

    }

    // for test.
    private void bark(offsetInfo off) {
        if (off == null) {
            System.out.println("not found");
        }
        else
            System.out.println(
                String.format("offsetInFile:%d, offsetInBlock:%d", off.offsetInfile, off.offsetInBlock)
        );
    }

    public static void main(String[] args) throws Exception {

        /*
        BufferManager bm = new BufferManager();
        IndexManager im = new IndexManager(bm);

        Attribute id = new Attribute("id", -1, true, true);
        Attribute name = new Attribute("name", 5, false, false);
        Attribute salary = new Attribute("salary", 0, false, false);

        Table tableInfo = new Table();
        tableInfo.totalLength = 13;
        tableInfo.blockNum = 3;
        tableInfo.attrNum = 3;
        tableInfo.name = "Person";
        tableInfo.attributes.add(id);
        tableInfo.attributes.add(name);
        tableInfo.attributes.add(salary);

        Index id_index = new Index();
        id_index.tableName = tableInfo.name;
        id_index.columnIndex = 0;
        id_index.indexName = "Person_id";
        id_index.columnLength = 4;
        */
//        id_index.rootBlockOffset = 2;

//         测试等值查找.
//        im.createIndex(tableInfo, id_index);
//        System.out.println(id_index.rootBlockOffset);
//        byte[] id_key = new byte[] {0, 0, 2, 2};
//        offsetInfo off = im.searchEqual(id_index, id_key);
//        im.bark(off);

//        byte[] new_key = new byte[] { 0, 0, 2, 3};
//        offsetInfo off = im.searchEqual(id_index, new_key);
//        im.bark(off);
//        im.deleteKey(id_index, new_key);
//        off = im.searchEqual(id_index, new_key);
//        im.bark(off);
    }

}
