import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Diva } from "../comp/core/Diva";
import { TextContent } from "../comp/core/TextContent";
import { TextField } from "../comp/core/TextField";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator, ValidatorRuleName } from "../Validator";

export class SearchFileSystemDlg extends DialogBase {
    static defaultSearchText: string = "";
    searchTextField: TextField;
    searchTextState: Validator = new Validator("", [
        { name: ValidatorRuleName.REQUIRED }
    ]);

    constructor() {
        super("Search File System");
        this.onMount(() => this.searchTextField?.focus());
        this.searchTextState.setValue(SearchFileSystemDlg.defaultSearchText);
        this.validatedStates = [this.searchTextState];
    }

    renderDlg(): CompIntf[] {
        return [
            new Diva([
                new TextContent("Enter text to find. Only content text will be searched. All sub-nodes under the selected node are included in the search."),
                this.searchTextField = new TextField({
                    label: "Search",
                    enter: this.searchNodes,
                    val: this.searchTextState
                }),
                new ButtonBar([
                    new Button("Search", this.searchNodes),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    searchNodes = async () => {
        if (!this.validate()) {
            return;
        }

        // until we have better validation
        const node = S.nodeUtil.getHighlightedNode();
        if (!node) {
            S.util.showMessage("No node is selected to search under.", "Warning");
            return;
        }

        SearchFileSystemDlg.defaultSearchText = this.searchTextState.getValue();

        const res = await S.rpcUtil.rpc<J.LuceneSearchRequest, J.LuceneSearchResponse>("luceneSearch", {
            nodeId: node.id,
            text: SearchFileSystemDlg.defaultSearchText
        });
        this.searchNodesResponse(res);
    }

    searchNodesResponse = (res: J.LuceneSearchResponse) => {
        S.util.showMessage(res.message, "Note", true);
        this.close();
    }
}
