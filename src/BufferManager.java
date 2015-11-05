import java.io.IOException;
import java.io.RandomAccessFile;

import static java.lang.System.*;

/**
 * Created by gyuu on 15/11/1.
 */
public class BufferManager {
    public static final byte EMPTY_FLAG = '@'; // 每条记录开头使用 '@' 表示是否为空.
    public static final int BUFFER_SIZE = 5; // 现在是 100.
    public static final int BLOCK_SIZE = BufferNode.BLOCK_SIZE;
    public BufferNode[] buffer = new BufferNode[BUFFER_SIZE];

    public BufferManager(){
        for (int i =0; i<BUFFER_SIZE; i++)
            buffer[i] = new BufferNode();
    }

    // 将缓冲区中指定序号的 BufferNode 写回文件, 并将该块清空.
    private void flashBack(int bufferIndex) throws IOException {
        if(!buffer[bufferIndex].isWritten){
            return;
        }
        String filename = buffer[bufferIndex].fileName;
        RandomAccessFile fout = new RandomAccessFile(filename, "rw");
        fout.seek(buffer[bufferIndex].blockOffset * BLOCK_SIZE);
        fout.write(buffer[bufferIndex].data);
        buffer[bufferIndex].initialize();
        fout.close();
    }

    // 根据文件名和块偏移, 寻找 buffer 中该块, 如果存在,返回该块,否则返回 null。
    public BufferNode getIfIsInBuffer(String fileName, int blockOffset) {
        for (int bufferIndex=0; bufferIndex<BUFFER_SIZE; bufferIndex++)
            if (buffer[bufferIndex].fileName.equals(fileName) && buffer[bufferIndex].blockOffset == blockOffset)
                return buffer[bufferIndex];
        return null;
    }

    // 根据文件名和块偏移, 返回该块.
    public BufferNode getBufferNode(String fileName, int blockOffset) {
        BufferNode node = getIfIsInBuffer(fileName, blockOffset);
        if (node == null) {
            try {
                node = getEmptyBufferNodeExcept(fileName);
                readBlock(fileName, blockOffset, node);
            }
            catch (IOException e){
                System.out.println("BufferManager: Requested file not found.");
                exit(0);
            }
        }
        return node;
    }

    // 根据文件名和块偏移, 申请一个块并将其初始化.
    public BufferNode createBufferNode(String fileName, int blockOffset) throws IOException {
        BufferNode node = getEmptyBufferNode();
        node.fileName = fileName;
        node.blockOffset = blockOffset;
        return node;
    }

    // 读入指定的块到 buffer 中指定的 node.
    public void readBlock(String fileName, int blockOffset, BufferNode node) throws IOException {
        node.isValid = true;
        node.isWritten = false;
        node.fileName = fileName;
        node.blockOffset = blockOffset;
        RandomAccessFile fin = new RandomAccessFile(fileName, "r");
        fin.seek(node.blockOffset * BLOCK_SIZE);
        // read 在读取 data.length 个字节就结束读取.
        fin.read(node.data);
        fin.close();
        writeBlock(node); // 将该块标记为已修改.
    }

    // 把一个块标记为已更新过, 并标记为最近使用.
    private void writeBlock(BufferNode node) {
        node.isWritten = true;
        useBlock(node);
    }

    // LRU 算法代价较高, 每次写一个块时, 将其 LRUValue 置为0, 将其他的全部块 LRUValue 递增.
    private void useBlock(BufferNode node) {
        node.LRUValue = 0;
        node.isValid = true;
        for (int i=0; i<BUFFER_SIZE; i++) {
            if (buffer[i] != node)
                buffer[i].LRUValue++; // LRUValue 越大, 说明最近访问越少.
        }
    }

    // 找出非 Valid(表示该块中存的数据是已经被删除的表或索引) 或者 LURValue 最高的块, 将其替换出去.
    // 返回一个新的可用的 block.
    public BufferNode getEmptyBufferNode() throws IOException {
        int bufferIndex = 0;
        int maxLRUValue = buffer[0].LRUValue;

        for (int i=0; i<BUFFER_SIZE; i++) {
            if (!buffer[i].isValid){
                buffer[i].isValid = true;
                return buffer[i];
            }
            else if (buffer[i].LRUValue > maxLRUValue) {
                maxLRUValue = buffer[i].LRUValue;
                bufferIndex = i;
            }
        }
        flashBack(bufferIndex);
        buffer[bufferIndex].isValid = true;
        return buffer[bufferIndex];
    }

    // 也是替换块, 但是限制了相同文件名的块无法被替换出去.
    public BufferNode getEmptyBufferNodeExcept(String fileName) throws IOException {
        int bufferIndex = -1;
        int maxLRUValue = buffer[0].LRUValue;
        for (int i=0; i<BUFFER_SIZE; i++){
            if (!buffer[i].isValid){
                buffer[i].isValid = true;
                return buffer[i];
            }
            // 缓冲区中相同文件名的块不能被替换?
            else if (buffer[i].LRUValue > maxLRUValue && !buffer[i].fileName.equals(fileName)){
                maxLRUValue = buffer[i].LRUValue;
                bufferIndex = i;
            }
        }
        // if no bufferNode can be replaced, break down.
        assert bufferIndex != -1 : "Buffer out of space, unable to allocate new room.";

        flashBack(bufferIndex);
        buffer[bufferIndex].isValid = true;
        return buffer[bufferIndex];
    }

    public void setInvalid(String fileName) {
        for (int i=0; i<BUFFER_SIZE; i++){
            if (buffer[i].fileName.equals(fileName)) {
                buffer[i].isValid = false;
                buffer[i].isWritten = false;
            }
        }
    }

    // 给记录对应的文件中增加一个块, 返回这个块.
    public BufferNode addBlockInFile(Table table_info) throws IOException {
        BufferNode node = getEmptyBufferNode();
        node.initialize();
        node.isValid = true;
        node.isWritten = true;
        node.fileName = table_info.name + ".table";
        node.blockOffset = table_info.blockNum++;
        return node;
    }

    // 给索引对应的文件增加一个块.
    public BufferNode addBlockInFile(Index index_info) throws IOException {
        String fileName = index_info.indexName + ".index";
        BufferNode node = getEmptyBufferNodeExcept(fileName);
        node.initialize();
        node.isValid = true;
        node.isWritten = true;
        node.fileName = fileName;
        node.blockOffset = index_info.blockNum++;
        return node;
    }

    // 读取表中的所有记录.
    public void readWholeTable(Table table_info) throws IOException {
        String fileName = table_info.name + ".table";
        for (int blockOffset=0; blockOffset < table_info.blockNum; blockOffset++) {
            if (getIfIsInBuffer(fileName, blockOffset) == null) {
                BufferNode node = getEmptyBufferNodeExcept(fileName);
                readBlock(fileName, blockOffset, node);
            }
        }
    }

    // 打印出缓冲区中的 Block 的数据.
    public void showBuffer(int index) {
        BufferNode node = buffer[index];
        String info = String.format("BufferIndex:%d, IsWritten:%b, IsValid:%b, FileName:%s, blockOffset:%d, LRU:%d",
                index, node.isWritten, node.isValid, node.fileName, node.blockOffset, node.LRUValue
                );
        System.out.println(info);
    }

    // for test.
    public void showBuffer(int start, int end) {
        for (int i=start; i<end; i++) {
            showBuffer(i);
        }
    }

    // for test.
    public static void main(String[] args) throws IOException {
        BufferManager bm = new BufferManager();
        bm.showBuffer(0, 5);
    }
}

class BufferNode {

    public static final int BLOCK_SIZE = 4096;
    public boolean isWritten;
    public boolean isValid;
    public String fileName;
    public int blockOffset;
    public int LRUValue;
    public byte[] data = new byte[BLOCK_SIZE+1];

//  不可使用默认初始化,因为字符串为 null 的话会很麻烦.

    public BufferNode() {
        isWritten = false;
        isValid = false;
        fileName = "";
        blockOffset = 0;
        LRUValue = 0;
    }

    public void initialize() {
        this.isWritten = false;
        this.isValid = false;
        this.fileName = "";
        this.blockOffset = 0;
        this.LRUValue = 0;
        // 清空字节数组.
        for (int i=0; i< BLOCK_SIZE; i++)
            data[i] = 0;
    }

    String getString(int start, int end) {
        byte[] tmp = new byte[end-start+1];
        for (int i=start; i<end; i++)
            tmp[i] = data[i];
        String res = new String(tmp);
        return res;
    }

    public void setInt(int pos, int length,int sourceInt){

        for(int i=0;i<length;i++){
            data[i+pos]=(byte)(sourceInt>>8*(3-i)&0xFF);
        }
        isWritten = true;
    }

    public int getInt(int pos, int length){
        int k=0;
        for(int i=0;i<length;i++){
            k  +=(data[i+pos] & 0xFF)<<(8*(3-i));
        }
        return k;
    }

    public byte[] getBytes(int startpos, int length){
        byte[] b = new byte[length];
        for(int i =0;i<length;i++){
            b[i]=data[startpos+i];
        }
        return b;
    }

    public void setBytes(int startpos, byte[] sourcebyte){
        //byte[] b = new byte[length];
        for(int i =0;i<sourcebyte.length;i++){
            data[startpos+i]=sourcebyte[i];
        }
        isWritten=true;
    }

    public void setInternalKey(int pos,byte[] key,int offset) {
        setBytes(pos,key);
        setInt(pos+key.length,4,offset);
        isWritten=true;
    }

    public  void setKeyData(int pos,byte[] insertKey,int blockOffset,int offset) {
        setInt(pos,4,blockOffset);
        setInt(pos+4,4,offset);
        setBytes(pos+8,insertKey);
        isWritten=true;
    }


    public static void main(String[] args) throws IOException {
    }
}