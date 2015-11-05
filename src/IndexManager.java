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
    public static void createIndex(Table tableInfo,Index indexInfo) throws IOException { //需要API提供表和索引信息结构

        BPlusTree thisTree=new BPlusTree(indexInfo, buf); //创建一棵新树

        //开始正式建立索引
        String filename=tableInfo.name+".table";
        try{
            for(int blockOffset=0; blockOffset< tableInfo.blockNum; blockOffset++){
                BufferNode block = buf.getBufferNode(filename, blockOffset);
                int recordNum = 4096 / tableInfo.totalLength; // int recordNum = block.recordNum;
                for(int offset =0; offset < recordNum /*tableInfo.maxPerRecordNum*/; offset++){
                    int position = offset*tableInfo.totalLength;
                    byte[] Record = block.getBytes(position, tableInfo.totalLength); //读取表中的每条记录
                    //if(Record.isEmpty()) break;
                    byte[] key=getColumnValue(tableInfo,indexInfo,Record); //找出索引值
                    thisTree.insert(key, blockOffset, offset); //插入树中
                }
            }
        }catch(NullPointerException e){
            System.err.println("must not be null for key.");
        }
        catch(Exception e){
            System.err.println("the index has not been created.");
        }

        indexInfo.rootBlockOffset=thisTree.myRootBlock.blockOffset;
//        CatalogManager.setIndexRoot(indexInfo.indexName, thisTree.myRootBlock.blockOffset);

        System.out.println("创建索引成功！");
    }

    //删除索引，即删除索引文件
    public static void dropIndex(String filename ){
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
    public static offsetInfo searchEqual(Index indexInfo, byte[] key) throws Exception{
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
    static public void insertKey(Index indexInfo,byte[] key,int blockOffset,int offset) throws Exception{
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
    static public void deleteKey(Index indexInfo,byte[] deleteKey) throws Exception{
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

    public static void main(String[] args) {
        BufferManager bm = new BufferManager();
        IndexManager im = new IndexManager(bm);
    }

}