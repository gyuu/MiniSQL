import java.io.File;
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
    public void flashBack(int bufferIndex) throws IOException {
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

    // 根据文件名和块偏移, 寻找该文件中某块在 buffer 中的序号.如果不存在,返回 -1.
    public int getIfIsInBuffer(String fileName, int blockOffset) {
        for (int bufferIndex=0; bufferIndex<BUFFER_SIZE; bufferIndex++)
            if (buffer[bufferIndex].fileName.equals(fileName) && buffer[bufferIndex].blockOffset == blockOffset)
                return bufferIndex;
        return -1;
    }

    // 如果指定块不在 buffer 中,将其读入 buffer 中, 返回序号.
    public int getBufferIndex(String fileName, int blockOffset) {
        int bufferIndex = getIfIsInBuffer(fileName, blockOffset);
        if (bufferIndex == -1) {
            try {
                bufferIndex = getEmptyBufferExcept(fileName);
                readBlock(fileName, blockOffset, bufferIndex);
            }
            catch (IOException e){
                System.out.println("BufferManager: Requested file not found.");
                exit(0);
            }
        }
        return bufferIndex;
    }

    // 读入指定的块到 buffer 中.
    public void readBlock(String fileName, int blockOffset, int bufferIndex) throws IOException {
        buffer[bufferIndex].isValid = true;
        buffer[bufferIndex].isWritten = false;
        buffer[bufferIndex].fileName = fileName;
        buffer[bufferIndex].blockOffset = blockOffset;
        RandomAccessFile fin = new RandomAccessFile(fileName, "r");
        fin.seek(buffer[bufferIndex].blockOffset * BLOCK_SIZE);
        // read 在读取 data.length 个字节就结束读取.
        fin.read(buffer[bufferIndex].data);
        fin.close();
    }

    // 把一个块标记为已更新过, 可以写回文件.
    public void writeBlock(int bufferIndex) {
        buffer[bufferIndex].isWritten = true;
        useBlock(bufferIndex);
    }

    // LRU 算法代价较高, 每次写一个块时, 将其 LRUValue 置为0, 将其他的全部块 LRUValue 递增.
    public void useBlock(int bufferIndex) {
        for (int i=0; i<BUFFER_SIZE; i++) {
            if (i == bufferIndex){
                buffer[bufferIndex].LRUValue = 0;
                buffer[bufferIndex].isValid = true;
            }
            else
                buffer[i].LRUValue++; // LRUValue 越大, 说明最近访问越少.
        }
    }

    // 找出非 Valid 或者 LURValue 最高的块, 将其替换出去.
    public int getEmptyBuffer() throws IOException {
        int bufferIndex = 0;
        int maxLRUValue = buffer[0].LRUValue;

        for (int i=0; i<BUFFER_SIZE; i++) {
            if (!buffer[i].isValid){
                buffer[i].isValid = true;
                return i;
            }
            else if (buffer[i].LRUValue > maxLRUValue) {
                maxLRUValue = buffer[i].LRUValue;
                bufferIndex = i;
            }
        }
        flashBack(bufferIndex);
        buffer[bufferIndex].isValid = true;
        return bufferIndex;
    }

    // 也是替换块, 但是限制了相同文件名的块无法被替换出去.
    public int getEmptyBufferExcept(String fileName) throws IOException {
        int bufferIndex = -1;
        int maxLRUValue = buffer[0].LRUValue;
        for (int i=0; i<BUFFER_SIZE; i++){
            if (!buffer[i].isValid){
                buffer[i].isValid = true;
                return i;
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
        return bufferIndex;
    }

    public void setInvalid(String fileName) {
        for (int i=0; i<BUFFER_SIZE; i++){
            if (buffer[i].fileName.equals(fileName)) {
                buffer[i].isValid = false;
                buffer[i].isWritten = false;
            }
        }
    }

    // 给记录对应的文件中增加一个块, 将新块的信息写入缓冲区, 但还并未写入文件.
    public int addBlockInFile(Table table_info) throws IOException {
        int bufferIndex = getEmptyBuffer();
        buffer[bufferIndex].initialize();
        buffer[bufferIndex].isValid = true;
        buffer[bufferIndex].isWritten = true;
        buffer[bufferIndex].fileName = table_info.name + ".rec";
        buffer[bufferIndex].blockOffset = table_info.blockNum++;
        return bufferIndex;
    }

    // 给索引对应的文件增加一个块.
    public int addBlockInFile(Index index_info) throws IOException {
        String fileName = index_info.indexName + ".idx";
        int bufferIndex = getEmptyBufferExcept(fileName);
        buffer[bufferIndex].initialize();
        buffer[bufferIndex].isValid = true;
        buffer[bufferIndex].isWritten = true;
        buffer[bufferIndex].fileName = fileName;
        buffer[bufferIndex].blockOffset = index_info.blockNum++;
        return bufferIndex;
    }

    // 这个函数还是意义不明.
    public InsertPos getInsertPosition(Table table_info) throws IOException {
        InsertPos pos = new InsertPos();
        if (table_info.blockNum == 0) {
            pos.bufferIndex = addBlockInFile(table_info);
            pos.position = 0;
            return pos;
        }
        String fileName = table_info.name + ".rec";
        int length = table_info.totalLength+1;
        int blockOffset = table_info.blockNum-1;
        int bufferIndex = getIfIsInBuffer(fileName, blockOffset);
        if (bufferIndex == -1) {
            bufferIndex = getEmptyBuffer();
            readBlock(fileName, blockOffset, bufferIndex);
        }
        int recordNum = BLOCK_SIZE / length;
        for (int offset = 0; offset < recordNum; offset++) {
            int curr_position = offset * length;
            byte isEmpty = buffer[bufferIndex].data[curr_position];
            if (isEmpty == EMPTY_FLAG) {
                pos.bufferIndex = bufferIndex;
                pos.position = curr_position;
                return pos;
            }
        }

        pos.bufferIndex = addBlockInFile(table_info);
        pos.position = 0;
        return pos;
    }

    // 读取表中的所有记录.
    public void readWholeTable(Table table_info) throws IOException {
        String fileName = table_info.name + ".rec";
        for (int blockOffset=0; blockOffset < table_info.blockNum; blockOffset++) {
            if (getIfIsInBuffer(fileName, blockOffset) == -1) {
                int bufferIndex = getEmptyBufferExcept(fileName);
                readBlock(fileName, blockOffset, bufferIndex);
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

        String fileName = "test";
        File f = new File(fileName);
        if (f.exists())
            f.delete();
        f.createNewFile();

        BufferManager bm = new BufferManager();
        byte[] data = new byte[BLOCK_SIZE];


        // 测试 flashBack, readBlock 和 LRU, 测试成功.
        for (int i=0; i<5; i++){
            data[i] = (byte)i;
            bm.buffer[i].fileName = fileName;
            bm.buffer[i].blockOffset = i;
            bm.buffer[i].data = data;
        }
        bm.showBuffer(0, 5);
        for (int i=4; i>=0; i--) {
            bm.writeBlock(i);
            bm.flashBack(i);
            bm.readBlock(fileName, i, i);
        }
        bm.showBuffer(0, 5);
        int buf_index = bm.getEmptyBuffer();
        System.out.println(buf_index);


//        for (byte b : bm.buffer[3].data){
//            if (b != 0)
//                System.out.println(b);
//        }

//        int buf_index = bm.getBufferIndex(fileName, 0);
//        System.out.println(buf_index);
//
//        buf_index = bm.getBufferIndex(fileName, 1);
//        System.out.println(buf_index);

//        // 对于不存在的文件名, 打印 Requested file not found, 退出程序.
//        buf_index = bm.getBufferIndex("2333", 0);
//        System.out.println(buf_index);
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

    byte getByte(int pos) {
        return data[pos];
    }



    public static void main(String[] args) throws IOException {
    }
}

class InsertPos {
    int bufferIndex;
    int position;
}