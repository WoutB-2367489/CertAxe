package eu.bosteels.certaxe.observability;

import eu.bosteels.certaxe.ct.LogList;

public interface ProgressDatabase {


    void append(Event appended);

    int getNextIndex(LogList list);

}
