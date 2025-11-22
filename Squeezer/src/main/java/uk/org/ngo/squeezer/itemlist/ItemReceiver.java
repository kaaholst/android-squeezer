package uk.org.ngo.squeezer.itemlist;
import java.util.List;
import java.util.Map;

public interface ItemReceiver<T>  {
    void onItemsReceived(int count, int start, Map<String, Object> parameters, List<T> items, Class<T> dataType);
}

