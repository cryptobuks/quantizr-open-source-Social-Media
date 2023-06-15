import * as J from "./JavaIntf";

export class ResultSetInfo {
    public results: J.NodeInfo[];
    public description: string;
    public prop: string;
    public timeRangeType: string;
    public node: J.NodeInfo;
    public page: number = 0;
    public endReached: boolean = false;
    public fuzzy: boolean;
    public caseSensitive: boolean;
    public searchText: string;
    public searchRoot: string;
    public searchType: string;
    public recursive: boolean;
    public sortField: string;
    public sortDir: string;
    public requirePriority: boolean;
    public requireAttachment: boolean;

    /* holds array of all parent items all the way up the tree (as far as user is authorized) */
    breadcrumbs: J.BreadcrumbInfo[];
}
