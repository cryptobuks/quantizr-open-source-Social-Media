package quanta.service;

import java.util.HashSet;
import org.bson.types.ObjectId;
import quanta.mongo.model.SubNode;

public class WordStats {

    public String word;
    public long count;

    /* for trending tab view data we count not the number of actual uses of a word, but the number of
    unique people who have used the word (to prevent 'gaming the system'), and this set holds the
    NodeIds of each user who has used this word */
    public HashSet<ObjectId> users = new HashSet<>();

    public void inc(SubNode node, boolean trending) {
        if (trending) {
            if (users.add(node.getOwner())) {
                count++;
            }
        } else {
            count++;
        }
    }

    public WordStats(String word) {
        this.word = word;
    }
}
