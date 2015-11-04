import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by gyuu on 15/11/3.
 */

public class IndexManager {
    public static final int POINTERLENGTH = 5;
    public static final int BLOCK_SIZE = 4096;
    public static final byte EMPTY_FLAG = '@';
    public BufferManager bm;

    public IndexManager(BufferManager buffer_manager){
        bm = buffer_manager;
    }

    public String getColumnValue(Table table_info, Index index_info, String row){
        // 读取索引对应的属性在一条记录中的值, 以字符串的形式返回.
        String colValue = null;
        int start = 0;
        int end = 0;
        for (int i=0; i<index_info.columnIndex; i++) {
            start = end;
            end += table_info.attributes.get(i).length;
        }
        for (int j = start; j < end && row.charAt(j) != EMPTY_FLAG; j++)
            colValue += row.charAt(j);
        return colValue;
    }

    public void createIndex(Table table_info, Index index_info) throws IOException {
        // 创建索引文件,以 .idx 为后缀名.
        String fileName = index_info.indexName + ".idx";
        File f = new File(fileName);
        if (!f.exists())
            f.createNewFile();
        // 向 buffer 申请一个空块, 存放根节点.
        int bufferIndex = bm.getEmptyBuffer();
        bm.buffer[bufferIndex].fileName = fileName;
        bm.buffer[bufferIndex].blockOffset = 0;
        bm.buffer[bufferIndex].isValid = true;
        bm.buffer[bufferIndex].isWritten = true;
        bm.buffer[bufferIndex].data[0] = 'R';
        bm.buffer[bufferIndex].data[1] = 'L';
        // in Java we do not need to explicitly set array to zero.
        // 申请 4 个 pointer 块.
        index_info.blockNum += 4;

        // 读取表记录中的所有数据, 构建 B+ 树.
        String recordFileName = table_info.name + ".rec";
        String rowString, key;
        // 每条记录第一个字节说明是否被删除.
        int length = table_info.totalLength + 1;
        // 一个块中最多存放记录数.
        int recordNum = BLOCK_SIZE / length;
        // table_info 中记录了该表占用了多少个 block, 此时迭代所有 block.
        for (int blockOffset = 0; blockOffset < table_info.blockNum; blockOffset++) {
            // 先查找该 block 是否已在 buffer 中.
            int buf_index = bm.getIfIsInBuffer(recordFileName, blockOffset);
            // 如果不在, 将其读入一个空块中.
            if (buf_index == -1) {
                buf_index = bm.getEmptyBuffer();
                bm.readBlock(recordFileName, blockOffset, buf_index);
            }
            for (int offset=0; offset < recordNum; offset++) {
                int position = offset*length;
                rowString = bm.buffer[buf_index].getData(position, position+length);
                if (rowString.charAt(0) == EMPTY_FLAG)
                    continue;
                rowString = rowString.substring(1);
                key = getColumnValue(table_info, index_info, rowString);
                IndexLeaf leaf_node = new IndexLeaf(key, blockOffset, offset);
                // Java 没有默认参数.
                insertValue(index_info, leaf_node, 0);
            }
        }
    }

    // 外部调用时, blockOffset 需要为 0.
    public IndexBranch insertValue(Index index_info, IndexLeaf node, int blockOffset) throws IOException {
        IndexBranch reBranch = new IndexBranch();
        String fileName = index_info.indexName + ".idx";
        int bufferIndex = bm.getBufferIndex(fileName, blockOffset);
        boolean isLeaf = (bm.buffer[bufferIndex].data[1] == 'L');

        if (isLeaf) {
            Leaf leaf = new Leaf(bm, bufferIndex, index_info);
            leaf.Insert(node);

            int recordLength = index_info.columnLength + 2 * POINTERLENGTH;
            int maxRecordNum = (BLOCK_SIZE - 6 - 3 * POINTERLENGTH) / recordLength;
            // need to split.
            if (leaf.recordNum > maxRecordNum) {
                if (leaf.isRoot) {
                    int newRootBufferIndex = leaf.bufferIndex;
                    leaf.bufferIndex = bm.addBlockInFile(index_info);
                    int newSiblingBufferIndex = bm.addBlockInFile(index_info);
                    Branch branchRoot = new Branch(bm, newRootBufferIndex);
                    Leaf newSibling = new Leaf(bm, newSiblingBufferIndex);

                    branchRoot.isRoot = true;
                    newSibling.isRoot = false;
                    leaf.isRoot = false;

                    branchRoot.pointerToFather = newSibling.pointerToFather = leaf.pointerToFather = 0;
                    branchRoot.columnLength = newSibling.columnLength = leaf.columnLength;

                    newSibling.previousSibling = bm.buffer[leaf.bufferIndex].blockOffset;
                    leaf.nextSibling = bm.buffer[newSibling.bufferIndex].blockOffset;
                    while (newSibling.nodeList.size() < leaf.nodeList.size()) {
                        IndexLeaf child = leaf.pop();
                        newSibling.Insert(child);
                    }

                    IndexBranch tmpNode = new IndexBranch();
                    tmpNode.key = newSibling.getFront().key;
                    tmpNode.pointerToChild = bm.buffer[newSibling.bufferIndex].blockOffset;
                    branchRoot.Insert(tmpNode);
                    tmpNode.key = leaf.getFront().key;
                    tmpNode.pointerToChild = bm.buffer[leaf.bufferIndex].blockOffset;
                    branchRoot.Insert(tmpNode);
                    return reBranch;
                }
                else {
                    int newSiblingBufferIndex = bm.addBlockInFile(index_info);
                    Leaf newSibling = new Leaf(bm, newSiblingBufferIndex);
                    newSibling.isRoot = false;
                    newSibling.pointerToFather = leaf.pointerToFather;
                    newSibling.columnLength = leaf.columnLength;

                    newSibling.nextSibling = leaf.nextSibling;
                    newSibling.previousSibling = bm.buffer[leaf.bufferIndex].blockOffset;
                    leaf.nextSibling = bm.buffer[newSibling.bufferIndex].blockOffset;
                    if (newSibling.nextSibling != 0) {
                        int newBufIndex = bm.getBufferIndex(fileName, newSibling.nextSibling);
                        Leaf leafNext = new Leaf(bm, newBufIndex, index_info);
                        leafNext.nextSibling = bm.buffer[newSibling.bufferIndex].blockOffset;
                    }
                    while (newSibling.nodeList.size() < leaf.nodeList.size()) {
                        IndexLeaf child = leaf.pop();
                        newSibling.nodeList.add(child);
                    }
                    reBranch.key = newSibling.getFront().key;
                    reBranch.pointerToChild = leaf.nextSibling;
                    return reBranch;
                }
            }
            // no need to split.
            else {
                return reBranch;
            }
        }
        // not a leaf.
        else {
            Branch branch = new Branch(bm, bufferIndex, index_info);
            Iterator<IndexBranch> iter = branch.nodeList.iterator();
            int i = 0;
            IndexBranch tmp = iter.next();
            if (tmp.key.compareTo(node.key) > 0)
                tmp.key = node.key;
            else {
                while (iter.hasNext()) {
                    if (iter.next().key.compareTo(node.key) > 0) break;
                    i++;
                }
                i--;
            }
            IndexBranch branch_node = insertValue(index_info, node, branch.nodeList.get(i).pointerToChild);

            if (branch_node.key.equals("")) {
                return reBranch;
            }
            // need to split.
            else {
                branch.Insert(branch_node);
                int recordLength = index_info.columnLength + POINTERLENGTH;
                int maxRecordNum = (BLOCK_SIZE - 6 - POINTERLENGTH) / recordLength;
                if (branch.recordNum > maxRecordNum) {

                    if (branch.isRoot) {
                        int newRootBufferIndex = branch.bufferIndex;
                        branch.bufferIndex = bm.addBlockInFile(index_info);
                        int newSiblingBufferIndex = bm.addBlockInFile(index_info);

                        Branch branchRoot = new Branch(bm, newRootBufferIndex);
                        Branch newSibling = new Branch(bm, newSiblingBufferIndex);

                        branchRoot.isRoot = true;
                        newSibling.isRoot = false;
                        branch.isRoot = false;

                        branchRoot.pointerToFather = newSibling.pointerToFather = branch.pointerToFather = 0;
                        branchRoot.columnLength = newSibling.columnLength = branch.columnLength;

                        while (newSibling.nodeList.size() < branch.nodeList.size()) {
                            IndexBranch child = branch.pop();
                            newSibling.Insert(child);
                        }

                        IndexBranch tmpNode = new IndexBranch();
                        tmpNode.key = newSibling.getFront().key;
                        tmpNode.pointerToChild = bm.buffer[newSibling.bufferIndex].blockOffset;
                        branchRoot.Insert(tmpNode);
                        tmpNode.key = branch.getFront().key;
                        tmpNode.pointerToChild = bm.buffer[branch.bufferIndex].blockOffset;
                        branchRoot.Insert(tmpNode);
                        return reBranch;
                    }
                    else {
                        int newSiblingBufIndex = bm.addBlockInFile(index_info);
                        Branch newSibling = new Branch(bm, newSiblingBufIndex);
                        newSibling.isRoot = false;
                        newSibling.pointerToFather = branch.pointerToFather;
                        newSibling.columnLength = branch.columnLength;

                        while (newSibling.nodeList.size() < branch.nodeList.size()) {
                            IndexBranch child = branch.pop();
                            newSibling.Insert(child);
                        }

                        reBranch.key = newSibling.getFront().key;
                        reBranch.pointerToChild = bm.buffer[newSibling.bufferIndex].blockOffset;
                        return reBranch;
                    }
                }
                else {
                    return reBranch;
                }
            }
        }
//        return reBranch;
    }

    public Data selectEqual(Table table_info, Index index_info, String key, int blockOffset) throws IOException {
        Data result = new Data();
        String fileName = index_info.indexName + ".idx";
        int bufferIndex = bm.getBufferIndex(fileName, blockOffset);
        boolean isLeaf = ( bm.buffer[bufferIndex].data[1] == 'L');
        if (isLeaf) {
            Leaf leaf = new Leaf(bm, bufferIndex, index_info);
            Iterator<IndexLeaf> iter = leaf.nodeList.iterator();
            while (iter.hasNext()) {
                IndexLeaf tmpLeaf = iter.next();
                if (tmpLeaf.key.equals(key)){
                    String recFileName = index_info.tableName + ".rec";
                    int recordBufIndex = bm.getBufferIndex(recFileName, tmpLeaf.offsetInFile);
                    int position = (table_info.totalLength+1) * (tmpLeaf.offsetInBlock);
                    String rowString = bm.buffer[recordBufIndex].getData(position, position+table_info.totalLength);
                    if (rowString.charAt(0) != EMPTY_FLAG) {
                        rowString = rowString.substring(1);
                        Row splitedRow = splitRow(table_info, rowString);
                        result.rows.add(splitedRow);
                        return result;
                    }
                }
            }
        }
        else {
            Branch branch = new Branch(bm, bufferIndex, index_info);
            Iterator<IndexBranch> iter = branch.nodeList.iterator();
            int i = 0;
            while (iter.hasNext()) {
                if (iter.next().key.compareTo(key) > 0){
                    i--;
                    break;
                }
                i++;
            }
            if (i == branch.nodeList.size())
                i--;
            result = selectEqual(table_info, index_info, key, branch.nodeList.get(i).pointerToChild);
        }
        return result;
    }

    public Data selectBetween(Table table_info, Index index_info, String keyFrom, String keyTo, int blockOffset) throws IOException {
        Data result = new Data();
        String fileName = index_info.indexName + ".idx";
        int bufferIndex = bm.getBufferIndex(fileName, blockOffset);
        boolean isLeaf = ( bm.buffer[bufferIndex].data[0] == 'L');
        if (isLeaf) {
            do {
                Leaf leaf = new Leaf(bm, bufferIndex, index_info);
                Iterator<IndexLeaf> iter = leaf.nodeList.iterator();
                while (iter.hasNext()) {
                    IndexLeaf tmpLeaf = iter.next();
                    if (tmpLeaf.key.compareTo(keyFrom) > 0) {
                        if (tmpLeaf.key.compareTo(keyTo) > 0)
                            return result;
                        String recFileName = index_info.tableName + ".rec";
                        int recordBufferIndex = bm.getBufferIndex(fileName, tmpLeaf.offsetInFile);
                        int position = (table_info.totalLength+1) * tmpLeaf.offsetInBlock;
                        String rowString = bm.buffer[recordBufferIndex].getData(position, position+table_info.totalLength);
                        if (rowString.charAt(0) != EMPTY_FLAG) {
                            rowString = rowString.substring(1);
                            Row splitedRow = splitRow(table_info, rowString);
                            result.rows.add(splitedRow);
                        }
                    }
                }
                if (leaf.nextSibling != 0) {
                    fileName = index_info.indexName + ".idx";
                    bufferIndex = bm.getBufferIndex(fileName, leaf.nextSibling);
                }
                else
                    return result;
            }while (true);
        }
        else {
            Branch branch = new Branch(bm, bufferIndex, index_info);
            Iterator<IndexBranch> iter = branch.nodeList.iterator();
            IndexBranch tmp = iter.next();
            if( tmp.key.compareTo(keyFrom) > 0) {
                result = selectBetween(table_info, index_info, keyFrom, keyTo, tmp.pointerToChild);
                return result;
            }
            else {
                int i = 1;
                while (iter.hasNext()) {
                    tmp = iter.next();
                    if (tmp.key.compareTo(keyFrom) > 0) {
                        i--;
                        break;
                    }
                    i++;
                }
                result = selectBetween(table_info, index_info, keyFrom, keyTo, tmp.pointerToChild);
                return result;
            }
        }
//        return result;
    }

    Row splitRow(Table table_info, String row) {
        Row splitedRow = new Row();
        int start, end;
        start = end = 0;
        for (int i=0; i<table_info.attrNum; i++) {
            start = end;
            end += table_info.attributes.get(i).length;
            String col = null;
            for (int j=start; j<end; j++){
                if (row.charAt(j) == EMPTY_FLAG)
                    break;
                col += row.charAt(j);
            }
            splitedRow.columns.add(col);
        }
        return splitedRow;
    }

    void dropIndex(Index index_info) {
        String fileName = index_info.indexName + ".idx";
        File f = new File(fileName);
        boolean delete_flag = false;
        if (f.exists())
            delete_flag = f.delete();
        assert delete_flag == true : "Error deleting file.";
        bm.setInvalid(fileName);
    }

//    void deleteValue() {
//
//    }

    public static void main(String[] args) {
        System.out.println("23333");
    }
}


class IndexLeaf {
    public String key;
    public int offsetInFile;
    public int offsetInBlock;

    public IndexLeaf() {
        offsetInBlock = 0;
        offsetInFile = 0;
    }

    public IndexLeaf(String s, int offsetf, int offsetb) {
        key = s;
        offsetInFile = offsetf;
        offsetInBlock = offsetb;
    }
}

class IndexBranch {
    public String key;
    public int pointerToChild;

    public IndexBranch() {
        pointerToChild = 0;
    }

    public IndexBranch(String s, int ptr) {
        key = s;
        pointerToChild = ptr;
    }
}

class BPlusTree {

    public static final byte EMPTY = '@';
    public static final int POINTERLENGTH = 5;
    // 无法声明全局变量, 存放一个指向 buffer 的引用.
    public BufferManager bm;
    public boolean isRoot;
    public int pointerToFather;
    public int recordNum;
    public int bufferIndex;
    public int columnLength;

    public BPlusTree(BufferManager buffer_manager) {
        bm = buffer_manager;
    }

    public BPlusTree(BufferManager buffer_manager, int bufIndex) {
        bm = buffer_manager;
        bufferIndex = bufIndex;
        recordNum = 0;
    }

    int getPointer(int pos) {
        int pointer = 0;
        for (int i=pos; i<pos+POINTERLENGTH; i++) {
            pointer  = pointer*10 + bm.buffer[bufferIndex].data[i] - '0';
        }
        return pointer;
    }

    int getRecordNum() {
        int recNum = 0;
        for (int i=2; i<6; i++) {
            if (bm.buffer[bufferIndex].data[i] == EMPTY)
                break;
            recNum = 10 * recNum + bm.buffer[bufferIndex].data[i] - '0';
        }
        System.out.println(String.format("RecordNum: %d", recNum));
        return recNum;
    }
}

class Branch extends BPlusTree {
    public List<IndexBranch> nodeList = new LinkedList<>();

    public Branch(BufferManager buffer_manager) {
        super(buffer_manager);
    }

    public Branch(BufferManager buffer_manager, int bufIndex) {
        super(buffer_manager, bufIndex);
    }

    // 待检查.
    public Branch(BufferManager buffer_manager, int bufIndex, Index index_info) {
        super(buffer_manager, bufIndex);
        isRoot = ( bm.buffer[bufferIndex].data[0] == 'R');
        int recordCount = getRecordNum();
        recordNum = 0;
        pointerToFather = getPointer(6);
        columnLength = index_info.columnLength;
        int position = 6 + POINTERLENGTH;
        for (int i=0; i<recordCount; i++) {
            String key = "";
            for (int j=position; j < position+POINTERLENGTH; j++) {
                if (bm.buffer[bufferIndex].data[j] == EMPTY)
                    break;
                else
                    key += bm.buffer[bufferIndex].data[j];
            }
            position += columnLength;
            int pointerToChild = getPointer(position);
            position += POINTERLENGTH;
            IndexBranch node = new IndexBranch(key, pointerToChild);
            Insert(node);
        }

    }

    // 是否可以工作待检查.
    void Insert(IndexBranch branch_node) {
        recordNum++;
        Iterator<IndexBranch> iter = nodeList.iterator();
        int i = 0;
        if (nodeList.size() == 0)
            nodeList.add(i, branch_node);
        else {
            while (iter.hasNext()) {
                if (iter.next().key.compareTo(branch_node.key) > 0)
                    break;
                i++;
            }
            nodeList.add(i, branch_node);
        }
    }

    // 弹出最后一个子节点, 可以写个小程序验证.
    IndexBranch pop() {
        recordNum--;
        IndexBranch node = nodeList.get(recordNum);
        nodeList.remove(recordNum);
        return node;
    }

    // 获得第一个子节点, 同上.
    IndexBranch getFront() {
        return nodeList.get(0);
    }

}

class Leaf extends BPlusTree {
    public int nextSibling;
    public int previousSibling;
    public List<IndexLeaf> nodeList;

    public Leaf(BufferManager buffer_manager, int bufIndex) {
        super(buffer_manager, bufIndex);
        nextSibling = previousSibling = 0;
    }

    public Leaf(BufferManager buffer_manager, int bufIndex, Index index_info) {
        super(buffer_manager, bufIndex);
        isRoot = ( bm.buffer[bufferIndex].data[0] == 'R');
        int recordCount = getRecordNum();
        recordNum = 0;
        pointerToFather = getPointer(6);
        previousSibling = getPointer(6+ POINTERLENGTH);
        nextSibling = getPointer(6+ 2*POINTERLENGTH);
        columnLength = index_info.columnLength;

        int position = 6 + 3*POINTERLENGTH;
        for (int i=position; i< recordCount; i++) {
            String key = "";
            for (int j=position; j < position+columnLength; j++) {
                if (bm.buffer[bufferIndex].data[j] == EMPTY)
                    break;
                else
                    key += bm.buffer[bufferIndex].data[j];
            }
            position += columnLength;
            int offsetInFile = getPointer(position);
            position += POINTERLENGTH;
            int offsetInBlock = getPointer(position);
            position += POINTERLENGTH;
            IndexLeaf node = new IndexLeaf(key, offsetInFile, offsetInBlock);
            Insert(node);
        }

    }

    void Insert(IndexLeaf leaf_node) {
        recordNum++;
        Iterator<IndexLeaf> iter = nodeList.iterator();
        int i = 0;
        if (nodeList.size() == 0)
            nodeList.add(i, leaf_node);
        else {
            while (iter.hasNext()) {
                if (iter.next().key.compareTo(leaf_node.key) > 0)
                    break;
                i++;
            }
            nodeList.add(i, leaf_node);
        }
    }

    IndexLeaf pop() {
        recordNum--;
        IndexLeaf node = nodeList.get(recordNum);
        nodeList.remove(recordNum);
        return node;
    }

    IndexLeaf getFront() {
        return nodeList.get(0);
    }
}