import { dispatch, getAs } from "../../AppContext";
import { Comp } from "../../comp/base/Comp";
import { Button } from "../../comp/core/Button";
import { ButtonBar } from "../../comp/core/ButtonBar";
import { Checkbox } from "../../comp/core/Checkbox";
import { Div } from "../../comp/core/Div";
import { Icon } from "../../comp/core/Icon";
import { IconButton } from "../../comp/core/IconButton";
import { Span } from "../../comp/core/Span";
import { Constants as C } from "../../Constants";
import { NodeActionType } from "../../intf/TypeIntf";
import * as J from "../../JavaIntf";
import { S } from "../../Singletons";

export class NodeCompButtonBar extends Div {

    constructor(public node: J.NodeInfo, public allowNodeMove: boolean, private extraButtons: Comp[], extraClass: string) {
        super(null, {
            id: "ncbb_" + node.id,
            className: "nodeCompButtonBar " + (extraClass || "")
        });
    }

    override preRender(): boolean {
        const ast = getAs();
        if (!this.node) {
            this.setChildren(null);
            return false;
        }

        let sharedIcon: Icon;
        let openButton: IconButton;
        let selCheckbox: Checkbox;
        let createSubNodeButton: Button;
        let editNodeButton: Button;
        let cutNodeIcon: Icon;
        let moveNodeUpIcon: Icon;
        let moveNodeDownIcon: Icon;
        let deleteNodeIcon: Icon;
        let pasteSpan: Span;

        const isPageRootNode = ast.node && this.node.id === ast.node.id;
        const type = S.plugin.getType(this.node.type);
        const specialAccountNode = type?.isSpecialAccountNode();
        if (specialAccountNode) this.allowNodeMove = false;
        let editingAllowed = S.edit.isEditAllowed(this.node);
        const actPubId = S.props.getPropStr(J.NodeProp.OBJECT_ID, this.node);
        let deleteAllowed = false;
        let editableNode = true;

        if (ast.isAdminUser) {
            editingAllowed = true;
            deleteAllowed = true;
            editableNode = true;
        }
        else if (type) {
            if (editingAllowed) {
                editingAllowed = type.allowAction(NodeActionType.editNode, this.node);
                deleteAllowed = type.allowAction(NodeActionType.delete, this.node);
                editableNode = type.allowAction(NodeActionType.editNode, this.node);
            }
        }
        else {
            if (editingAllowed) {
                deleteAllowed = true;
            }
        }

        /* DO NOT DELETE
            todo-2: need to make this if condition:
             if ((state.isAdminUser || S.props.isMine(node, state)) && S.props.isShared(node)) {
            show cause a clickable link to show up on the "shared to: " text
            to run the editNodeSharing()
           (I may bring this back eventually, but for now the fact that the sharing is being presented
            in the header of each node we don't need this icon and popup text )
        if (S.props.isShared(node)) {
            let sharingNames = S.util.getSharingNames(node, true);
            sharedIcon = new Icon({
                className: "fa fa-share-alt fa-lg rowIcon",
                onClick: () => S.edit.editNodeSharing(state, node),
                title: "Shared to:\n" + sharingNames
            });
        }
        */

        const isInlineChildren = !!S.props.getPropStr(J.NodeProp.INLINE_CHILDREN, this.node);

        /*
        We always enable for fs:folder, to that by clicking to open a folder that will cause the server to re-check and see if there are
        truly any files in there or not because we really cannot possibly know until we look. The only way to make this Open button
        ONLY show when there ARE truly children fore sure would be to force a check of the file system for every folder type that is ever rendered
        on a page and we don't want to burn that much CPU just to prevent empty-folders from being explored. Empty folders are rare.
        */
        if (this.node.hasChildren && !isPageRootNode &&
            // If children are shown inline, no need to allow 'open' button in this case unless we're in edit mode
            (!isInlineChildren || ast.userPrefs.editMode)) {
            openButton = new IconButton("fa-folder-open", "Open", {
                [C.NODE_ID_ATTR]: this.node.id,
                onClick: S.nav.openNodeById,
                title: "Explore content of this node"
            }, "btn-primary");
        }

        /*
         * If in edit mode we always at least create the potential (buttons) for a user to insert content, and if
         * they don't have privileges the server side security will let them know. In the future we can add more
         * intelligence to when to show these buttons or not.
         */
        if (ast.userPrefs.editMode) {
            const checkboxForEdit = editingAllowed && (ast.isAdminUser || S.render.allowAction(type, NodeActionType.editNode, this.node));
            const checkboxForDelete = ast.isAdminUser || deleteAllowed;

            if ((checkboxForEdit || checkboxForDelete) &&
                // no need to ever select home node, or special nodes
                this.node.id !== ast.node.id && !specialAccountNode) {
                selCheckbox = new Checkbox(null, {
                    title: "Select Nodes."
                }, {
                    setValue: (checked: boolean) => {
                        dispatch("NodeCheckboxChange", s => {
                            if (checked) {
                                s.selectedNodes.add(this.node.id);
                            } else {
                                s.selectedNodes.delete(this.node.id);
                            }
                        });
                    },
                    getValue: (): boolean => ast.selectedNodes.has(this.node.id)
                }, "float-start");
            }

            let insertAllowed = true;

            // if this is our own account node, we can always leave insertAllowed=true
            if (ast.userProfile?.userNodeId !== this.node.id) {
                if (type) {
                    insertAllowed = ast.isAdminUser || type.allowAction(NodeActionType.insert, this.node);
                }
            }
            const editInsertAllowed = S.props.isWritableByMe(this.node);

            if (C.NEW_ON_TOOLBAR && insertAllowed && editInsertAllowed && !actPubId) {
                createSubNodeButton = new Button(null, S.edit.newSubNode, {
                    [C.NODE_ID_ATTR]: this.node.id,
                    title: "Create new SubNode"
                }, null, "fa-plus");
            }

            const userCanPaste = S.props.isMine(this.node) || ast.isAdminUser || this.node.id === ast.userProfile?.userNodeId;

            if (editingAllowed) {
                if (editableNode && !specialAccountNode) {
                    editNodeButton = new Button(null, S.edit.runEditNodeByClick, {
                        title: "Edit Node",
                        [C.NODE_ID_ATTR]: this.node.id
                    }, null, "fa-edit");
                }

                if (!isPageRootNode && this.node.type !== J.NodeType.REPO_ROOT && !ast.nodesToMove) {
                    cutNodeIcon = new Icon({
                        className: "fa fa-cut fa-lg buttonBarIcon",
                        title: "Cut selected Node(s) to paste elsewhere.",
                        [C.NODE_ID_ATTR]: this.node.id,
                        onClick: S.edit.cutSelNodes
                    });
                }

                if (C.MOVE_UPDOWN_ON_TOOLBAR && this.allowNodeMove) {
                    if (this.node.logicalOrdinal > 0) {
                        moveNodeUpIcon = new Icon({
                            className: "fa fa-lg fa-arrow-up buttonBarIcon",
                            title: "Move Node Up",
                            [C.NODE_ID_ATTR]: this.node.id,
                            onClick: S.edit.moveNodeUp
                        });
                    }

                    if (!this.node.lastChild && ast.node.children && ast.node.children.length > 1) {
                        moveNodeDownIcon = new Icon({
                            className: "fa fa-lg fa-arrow-down buttonBarIcon",
                            title: "Move Node Down",
                            [C.NODE_ID_ATTR]: this.node.id,
                            onClick: S.edit.moveNodeDown
                        });
                    }
                }
            }

            if (deleteAllowed) {
                // not this user's own account node!
                if (this.node.id !== ast.userProfile?.userNodeId) {
                    deleteNodeIcon = new Icon({
                        className: "fa fa-trash fa-lg buttonBarIcon",
                        title: "Delete node(s)",
                        [C.NODE_ID_ATTR]: this.node.id,
                        onClick: S.edit.deleteSelNodes
                    });
                }
            }

            if (!!ast.nodesToMove && userCanPaste) {
                pasteSpan = new Span(null, { className: "float-end marginLeft" }, [
                    new Button("Paste Inside",
                        S.edit.pasteSelNodesInside, { [C.NODE_ID_ATTR]: this.node.id }, "btn-secondary pasteButton"),

                    this.node.id !== ast.userProfile?.userNodeId
                        ? new Button("Paste Here", S.edit.pasteSelNodes_InlineAbove, { [C.NODE_ID_ATTR]: this.node.id }, "btn-secondary pasteButton") : null
                ]);
            }
        }

        let upLevelButton: IconButton;
        let prevButton: Button;
        let nextButton: Button;
        const isMine = S.props.isMine(this.node);

        // Note we only allow 'Up Level' on home node if we're the admin.
        if (isPageRootNode &&
            (
                ((isMine || (this.node.type !== J.NodeType.POSTS && this.node.type !== J.NodeType.ACT_PUB_POSTS)) && this.node.name !== "home") ||
                ast.isAdminUser
            )) {
            if (S.nav.parentVisibleToUser()) {
                upLevelButton = new IconButton("fa-folder", "Up Level", {
                    [C.NODE_ID_ATTR]: this.node.id,
                    onClick: S.nav.navUpLevelClick,
                    title: "Go to Parent Node"
                }, "btn-primary");
            }

            // -----------------------
            // DO NOT DELETE!!
            // This code works perfecly but I realized I never use it AND more importantly a "Next Sibling on Tree" (and Prev)
            // is going to be too confusing to users, because they're too accustomed to "vertical" rather than "horizontal"
            // navigation so to speak...and next-sibiliong is definitely sort of "horizontal[ish]"
            // There are TWO places in the code I've removed this.
            //
            // const pageRootType = S.plugin.getType(ast.node.type);
            // if (!(pageRootType && pageRootType.isSpecialAccountNode()) && !S.nav.displayingRepositoryRoot(ast)) {
            //     prevButton = new Button(null, S.nav.navToPrev, {
            //         className: "fa fa-chevron-circle-left",
            //         title: "Prev"
            //     });

            //     nextButton = new Button(null, S.nav.navToNext, {
            //         className: "fa fa-chevron-circle-right",
            //         title: "Next"
            //     });
            // }
            // -----------------------
        }

        // ---------------------------
        // DO NOT DELETE
        // These buttons were moved to the main tree header bar, BUT if we ever decide to bring these back
        // for ALL nodes, rather than just the page root node, we can just re-enable this code.
        // if (isPageRootNode && this.node.hasChildren) {
        //     docIcon = !ast.isAnonUser ? new Icon({
        //         className: "fa fa-book fa-lg buttonBarIcon",
        //         title: "Show Document View",
        //         [C.NODE_ID_ATTR]: this.node.id,
        //         onClick: S.nav.openDocumentView
        //     }) : null;

        //     searchIcon = new Icon({
        //         className: "fa fa-search fa-lg buttonBarIcon",
        //         title: "Search Subnodes",
        //         ni[C.NODE_ID_ATTR]d: this.node.id,
        //         onClick: S.nav.runSearch
        //     });

        //     timelineIcon = !ast.isAnonUser ? new Icon({
        //         className: "fa fa-clock-o fa-lg buttonBarIcon",
        //         title: "View Timeline (by Mod Time)",
        //         [C.NODE_ID_ATTR]: this.node.id,
        //         onClick: S.nav.runTimeline
        //     }) : null;
        // }
        // ---------------------------

        const spanArray = [moveNodeUpIcon, //
            moveNodeDownIcon, cutNodeIcon, deleteNodeIcon,
            /* DO NOT DELETE: docIcon, searchIcon, timelineIcon, */
            pasteSpan];

        let floatEndSpan = null;
        if (spanArray.some(c => !!c)) {
            floatEndSpan = new Span(null, { className: "float-end" }, spanArray);
        }

        let btnArray: Comp[] = [openButton, upLevelButton, createSubNodeButton, editNodeButton, //
            prevButton, nextButton, floatEndSpan
        ];

        btnArray = btnArray.concat(this.extraButtons);

        let buttonBar = null;
        if (btnArray.some(c => !!c)) {
            buttonBar = new ButtonBar(btnArray, null, "marginLeftIfNotFirst");
        }

        if (selCheckbox || sharedIcon || buttonBar) {
            this.setChildren([selCheckbox, sharedIcon, buttonBar]);
            return true;
        }
        return false;
    }
}
