package quanta.response;

import java.util.LinkedList;
import java.util.List;
import quanta.model.BreadcrumbInfo;
import quanta.model.NodeInfo;
import quanta.response.base.ResponseBase;

public class RenderDocumentResponse extends ResponseBase {

    private List<NodeInfo> searchResults;
    private LinkedList<BreadcrumbInfo> breadcrumbs;

    public List<NodeInfo> getSearchResults() {
        return this.searchResults;
    }

    public void setSearchResults(final List<NodeInfo> searchResults) {
        this.searchResults = searchResults;
    }

    public LinkedList<BreadcrumbInfo> getBreadcrumbs() {
        return breadcrumbs;
    }

    public void setBreadcrumbs(LinkedList<BreadcrumbInfo> breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
    }

    public RenderDocumentResponse() {}
}
