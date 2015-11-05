

public class Condition {
    final static int OPERATION_EQUAL = 0;
    final static int OPERATION_NOT_EQUAL = 1;
    final static int OPERATION_LESS = 2;
    final static int OPERATION_MORE = 3;
    final static int OPERATION_LESS_EQUAL = 4;
    final static int OPERATION_MORE_EQUAL = 5;

    Condition (String a, String v, int o)
    {
        attribute_name = a;
        value = v;
        operate = o;
    }

    String attribute_name;
    String value;
    int operate;

    boolean if_right(int content)
    {
        int tmp = 0;
        tmp = Integer.parseInt(value);

        switch (operate)
        {
            case OPERATION_EQUAL:
                return content == tmp;
            case OPERATION_NOT_EQUAL:
                return content != tmp;
            case OPERATION_LESS:
                return content < tmp;
            case OPERATION_MORE:
                return content > tmp;
            case OPERATION_LESS_EQUAL:
                return content >= tmp;
            case OPERATION_MORE_EQUAL:
                return content <= tmp;
            default:
                return true;
        }
    }

    boolean if_right(float content)
    {
        float tmp = 0;
        tmp = Float.parseFloat(value);

        switch (operate)
        {
            case OPERATION_EQUAL:
                return content == tmp;
            case OPERATION_NOT_EQUAL:
                return content != tmp;
            case OPERATION_LESS:
                return content < tmp;
            case OPERATION_MORE:
                return content > tmp;
            case OPERATION_LESS_EQUAL:
                return content >= tmp;
            case OPERATION_MORE_EQUAL:
                return content <= tmp;
            default:
                return true;
        }
    }

    boolean if_right(String content)
    {
        String tmp = value;
        switch (operate)
        {
            case OPERATION_EQUAL:
                return content.equals(tmp);
            case OPERATION_NOT_EQUAL:
                return !content.equals(tmp);
            case OPERATION_LESS:
                return content.compareTo(tmp) < 0;
            case OPERATION_MORE:
                return content.compareTo(tmp) > 0;
            case OPERATION_LESS_EQUAL:
                return content.compareTo(tmp) <= 0;
            case OPERATION_MORE_EQUAL:
                return content.compareTo(tmp) >= 0;
            default:
                return true;
        }
    }
}
