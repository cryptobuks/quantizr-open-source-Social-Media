package quanta.lucene;

import java.io.IOException;
import java.nio.file.Paths;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import quanta.config.AppProp;
import quanta.util.StreamUtil;

// todo-2: make this a prototype-scope bean?
/**
 * Searches files indexed by Lucene (i.e. a Lucene Search). This code assumes that Lucene index
 * already exists.
 *
 * todo-2: Take another look at synchornized blocks in this code. I'm troubleshooting and putting in
 * temporary sync code right now.
 *
 * todo-2: - need ability to search only specific fields (path, content, date?) - need ability to
 * order either by score or by date (rev chron)
 */
@Component
public class FileSearcher {

    private static Logger log = LoggerFactory.getLogger(FileSearcher.class);

    @Autowired
    public AppProp appProp;

    private StringBuilder output = new StringBuilder();

    public String search(String luceneDataFoler, String line) {
        log.debug("searching for: " + line);
        String field = "contents";
        int hitsPerPage = 10;
        /**
         * todo-2: Is it more efficient (or even threadsafe?) to hold one or more of these two resources
         * open for multiple searches, and potentially simultaneous/threads?
         */
        FSDirectory fsDir = null;
        IndexReader reader = null;
        try {
            fsDir = FSDirectory.open(Paths.get(appProp.getLuceneDir() + "/" + luceneDataFoler));
            reader = DirectoryReader.open(fsDir);
            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser(field, analyzer);
            Query query = parser.parse(line);
            doPagingSearch(searcher, query, hitsPerPage);
        } catch (Exception e) {
            log.error("Searching failed.", e);
        } finally {
            StreamUtil.close(reader, fsDir);
        }
        return output.toString();
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine presents pages of
     * size n to the user. The user can then go to the next page if interested in the next hits.
     */
    public void doPagingSearch(IndexSearcher searcher, Query query, int hitsPerPage) throws IOException {
        TopDocs results = searcher.search(query, 100);
        ScoreDoc[] hits = results.scoreDocs;
        int numTotalHits = Math.toIntExact(results.totalHits.value);
        write(numTotalHits + " total matching documents");
        for (int i = 0; i < hits.length; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String path = doc.get("path");
            if (path != null) {
                write(String.valueOf(i + 1) + ". " + path);
                String title = doc.get("title");
                if (title != null) {
                    write("   Title: " + doc.get("title"));
                }
            } else {
                write(String.valueOf(i + 1) + ". No path for this document");
            }
        }
    }

    public void write(String msg) {
        output.append(msg);
        output.append("\n");
    }
}
