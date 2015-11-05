import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class Interpreter
{

    static int pos=0;


    public static int interprete(String s)
    {
        int tmp = 0;
        String word = "";

        word = get_word(s);

        if (word.equals("create"))
        {
            word = get_word(s);

            if (word.equals("table"))
            {
                String primary_key = "";
                String table_name = "";
                word = get_word(s);
                if (!word.isEmpty())
                    table_name = word;
                else
                {
                    System.out.println("Syntax Error: A table name is needed!");
                    return 0;
                }

                word = get_word(s);
                if (word.isEmpty() || !word.equals("("))
                {
                    System.out.println("Syntax Error");
                    return 0;
                }
                else
                {
                    word = get_word(s);
                    List<Attribute> attribute_list = new LinkedList<Attribute>();
                    while (!word.isEmpty() && !word.equals("primary") && !word.equals(")"))
                    {
                        String attribute_name = word;
                        int type = 0;
                        boolean unique = false;
                        word = get_word(s);
                        if (word.equals("int"))
                            type = 0;
                        else if (word.equals("float"))
                            type = -1;
                        else if (word.equals("char"))
                        {
                            word = get_word(s);
                            if (!word.equals("("))
                            {
                                System.out.println("Syntax Error: Unknown data type!");
                                return 0;
                            }
                            word = get_word(s);
                            int char_type;
                            try {
                                char_type = Integer.parseInt(word);
                            } catch (NumberFormatException e){
                                System.out.println("Syntax Error: Illegal format in char()!");
                                return 0;
                            }
                            type = char_type;
                            word =  get_word(s);
                            if (!word.equals(")"))
                            {
                                System.out.println("Syntax Error: Unknown data type!");
                                return 0;
                            }
                        }
                        else
                        {
                            System.out.println("Syntax Error: Unknown or missing data type!");
                            return 0;
                        }
                        word = get_word(s);
                        if (word.equals("unique"))
                        {
                            unique = true;
                            word  = get_word(s);
                        }
                        // problem!!!
                        Attribute attr = new Attribute(attribute_name, type, 32, false,unique);
                        attribute_list.add(attr);
                        if (!word.equals(","))
                        {
                            if (!word.equals(")"))
                            {
                                System.out.println("Syntax Error!");
                                return 0;
                            }
                            else
                                break;
                        }

                        word = get_word(s);
                    }
                    int primary_location = 0;
                    if (word.equals("primary"))
                    {
                        word = get_word(s);
                        if (!word.equals("key"))
                        {
                            System.out.println("Syntax Error!");
                            return 0;
                        }
                        else {
                            word = get_word(s);
                            if (word.equals("("))
                            {
                                word = get_word(s);
                                primary_key = word;
                                int i = 0;
                                for (i=0;i<attribute_list.size();i++)
                                {
                                    if (primary_key.equals(attribute_list.get(i).name))
                                    {
                                        attribute_list.get(i).unique = true;
                                        break;
                                    }
                                }
                                if (i == attribute_list.size())
                                {
                                    System.out.println("Syntax Error: primarykey does not exist!");
                                    return 0;
                                }
                                primary_location = i;
                                word = get_word(s);
                                if (!word.equals(")"))
                                {
                                    System.out.println("Syntax Error!");
                                    return 0;
                                }
                            }
                            else
                            {
                                System.out.println("Syntax Error!");
                                return 0;
                            }
                            word =get_word(s);
                            if (!word.equals(")"))
                            {
                                System.out.println("Syntax Error!");
                                return 0;
                            }
                        }
                    }
                    else if (word.isEmpty())
                    {
                        System.out.println("Syntax Error: a ) is needed!");
                        return 0;
                    }

                    System.out.println("Table is created!");
                    return 1;
                }
            }

            //创建 Index
            else if (word.equals("index"))
            {
                String table_name = "";
                String index_name = "";
                String attribute_name = "";
                word = get_word(s);
                if (!word.isEmpty())
                    index_name = word;
                else
                {
                    System.out.println("Syntax Error!");
                    return 0;
                }

                word = get_word(s);
                try
                {
                    if (!word.equals("on"))
                        throw new SyntaxException();
                    word = get_word(s);
                    if (word.isEmpty())
                        throw new SyntaxException();
                    table_name = word;
                    word = get_word(s);
                    if (!word.equals("("))
                        throw new SyntaxException();
                    word = get_word(s);
                    if (word.isEmpty())
                        throw new SyntaxException();
                    attribute_name = word;
                    word = get_word(s);
                    if (word.equals(")"))
                        throw new SyntaxException();
                    System.out.println("Index created");
                    return 1;
                } catch (SyntaxException e) {
                    System.out.println("Syntax Error!");
                    return 0;
                }
            }
            else
            {
                System.out.println("Syntax Error!");
                return 0;
            }
        }

        else if (word.equals("select"))
        {
            List<String> attr_selected = new LinkedList<String>();
            String table_name;
            word = get_word(s);
            if (!word.equals("*"))
            {
                while (!word.equals("from"))
                {
                    attr_selected.add(word);
                    word = get_word(s);
                }
            }
            else
                word = get_word(s);

            if (!word.equals("from"))
            {
                System.out.println("Syntax Error!");
                return 0;
            }

            word = get_word(s);
            if (!word.isEmpty())
                table_name = word;
            else
            {
                System.out.println("Syntax Error!");
                return 0;
            }

            word = get_word(s);
            if (word.isEmpty())
            {
                if (attr_selected.size() == 0)
                    System.out.println("Show Record!");
                else
                    System.out.println("Show Record!");
                return 1;
            }
            else if (word.equals("where"))
            {
                String attr_name = "";
                String value = "";
                int operate = Condition.OPERATION_EQUAL;
                List<Condition> condition_list = new LinkedList<Condition>();
                word = get_word(s);
                while (true)
                {
                    try{
                        if (word.isEmpty())
                            throw new SyntaxException();
                        attr_name = word;
                        word = get_word(s);
                        if (word.equals("<="))
                            operate = Condition.OPERATION_LESS_EQUAL;
                        else if (word.equals(">="))
                            operate = Condition.OPERATION_MORE_EQUAL;
                        else if (word.equals("="))
                            operate = Condition.OPERATION_EQUAL;
                        else if (word.equals("<>"))
                            operate = Condition.OPERATION_NOT_EQUAL;
                        else if (word.equals("<"))
                            operate = Condition.OPERATION_LESS;
                        else if (word.equals(">"))
                            operate = Condition.OPERATION_MORE;
                        else
                            throw new SyntaxException();
                        word = get_word(s);
                        if (word.isEmpty())
                            throw new SyntaxException();
                        value = word;
                        Condition tmp_c = new Condition(attr_name,value,operate);
                        condition_list.add(tmp_c);
                        word = get_word(s);
                        if (word.isEmpty())
                            break;
                        if (!word.equals("and"))
                            throw new SyntaxException();
                        word = get_word(s);
                    } catch (SyntaxException e) {
                        System.out.println("Syntax Error!");
                        return 0;
                    }
                }
                if (attr_selected.size() == 0)
                    System.out.println("Selected!");
                else
                    System.out.println("Selected!");
                return 1;
            }
        }

        else if (word.equals("drop"))
        {
            word = get_word(s);
            if (word.equals("table"))
            {
                word = get_word(s);
                if (!word.isEmpty())
                {
                    System.out.println("Drop table!");
                    return 1;
                }
                else
                {
                    System.out.println("Syntax Error!");
                    return 0;
                }
            }
            else if (word.equals("index"))
            {
                word = get_word(s);
                if (!word.isEmpty())
                {
                    System.out.println("Drop index!");
                    return 1;
                }
                else
                {
                    System.out.println("Syntax Error!");
                    return 0;
                }
            }
            else
            {
                System.out.println("Syntax Error!");
                return 0;
            }
        }

        else if (word.equals("delete"))
        {
            String table_name = "";
            word = get_word(s);
            if (!word.equals("from"))
            {
                System.out.println("Syntax Error!");
                return 0;
            }

            word = get_word(s);
            if (!word.isEmpty())
                table_name=word;
            else
            {
                System.out.println("Syntax Error!");
                return 0;
            }

            word = get_word(s);
            if (word.isEmpty())
            {
                System.out.println("Delete table!");
                return 1;
            }
            else if (word.equals("where"))
            {
                String attr_name = "";
                String value = "";
                int operate = Condition.OPERATION_EQUAL;
                List<Condition> condition_list = new LinkedList<Condition>();
                word = get_word(s);
                while (true)
                {
                    try{
                        if (word.isEmpty())
                            throw new SyntaxException();
                        attr_name = word;
                        word = get_word(s);
                        if (word.equals("<="))
                            operate = Condition.OPERATION_LESS_EQUAL;
                        else if (word.equals(">="))
                            operate = Condition.OPERATION_MORE_EQUAL;
                        else if (word.equals("="))
                            operate = Condition.OPERATION_EQUAL;
                        else if (word.equals("<>"))
                            operate = Condition.OPERATION_NOT_EQUAL;
                        else if (word.equals("<"))
                            operate = Condition.OPERATION_LESS;
                        else if (word.equals(">"))
                            operate = Condition.OPERATION_MORE;
                        else
                            throw new SyntaxException();
                        word = get_word(s);
                        if (word.isEmpty())
                            throw new SyntaxException();
                        value = word;
                        Condition tmp_c = new Condition(attr_name,value,operate);
                        condition_list.add(tmp_c);
                        word = get_word(s);
                        if (word.isEmpty())
                            break;
                        if (!word.equals("and"))
                            throw new SyntaxException();
                        word = get_word(s);
                    } catch (SyntaxException e) {
                        System.out.println("Syntax Error!");
                        return 0;
                    }
                }

                System.out.println("Deleted!");
                return 1;
            }
        }

        else if (word.equals("insert"))
        {
            String table_name = "";
            List<String> value_list = new LinkedList<String>();
            word = get_word(s);
            try{
                if (!word.equals("into"))
                    throw new SyntaxException();
                word = get_word(s);
                if (word.isEmpty())
                    throw new SyntaxException();
                table_name = word;
                word = get_word(s);
                if (!word.equals("value"))
                    throw new SyntaxException();
                word = get_word(s);
                if (word.equals("("))
                    throw new SyntaxException();
                word = get_word(s);
                while (!word.isEmpty() && !word.equals(")"))
                {
                    value_list.add(word);
                    word = get_word(s);
                    if (word.equals(","))
                        word = get_word(s);
                }
                if (!word.equals(")"))
                    throw new SyntaxException();
            } catch (SyntaxException e) {
                System.out.println("Syntax Error!");
                return 0;
            }
            System.out.println("Table inserted!");
            return 1;
        }

        else if (word.equals("quit"))
            return -1;
        else if (word.equals("commit"))
            return 2;
        else if (word.equals("execfile"))
            return 3;
        else
        {
            if (!word.equals(""))
                System.out.println("There is no such command found!");
            return 0;
        }
        return 0;
    }

    private static String get_word(String s){
        String word = "";
        int idx1,idx2;

        if (pos >= s.length())
            return word;

        while ((s.charAt(pos) == ' ' || s.charAt(pos) == 10 || s.charAt(pos) == '\t') && s.charAt(pos) != 0){
            pos++;
        }
        idx1 = pos;

        if (s.charAt(pos) == '(' || s.charAt(pos) == ')' || s.charAt(pos) == ','){
            pos ++;
            idx2 = pos;
            word = s.substring(idx1, idx2);
            return word;
        }
        else if (s.charAt(pos) == 39){
            pos ++;
            while (s.charAt(pos) != 39 && s.charAt(pos) !=0)
                pos ++;
            if (s.charAt(pos) == 39){
                idx1 ++;
                idx2 = pos;
                pos ++;
                word = s.substring(idx1,idx2);
                return word;
            }
            else{
                word = "";
                return word;
            }
        }
        else{
            while (s.charAt(pos) != '(' && s.charAt(pos) != ')' && s.charAt(pos) != ' ' && s.charAt(pos) != ',' && s.charAt(pos) != 0 && s.charAt(pos) != 10){
                pos++;
                if (pos >= s.length())
                    break;
            }
            idx2 = pos;
            if (idx1 != idx2)
                word = s.substring(idx1,idx2);
            else
                word = "";
            return word;
        }
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        String quest = "";
        quest = in.nextLine();
        interprete(quest);
    }

}
