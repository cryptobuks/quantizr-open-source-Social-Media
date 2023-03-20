import { dispatch, getAs } from "../AppContext";
import { ScrollPos } from "../comp/base/Comp";
import { CompIntf } from "../comp/base/CompIntf";
import { Button } from "../comp/core/Button";
import { ButtonBar } from "../comp/core/ButtonBar";
import { Div } from "../comp/core/Div";
import { TextArea } from "../comp/core/TextArea";
import { DialogBase } from "../DialogBase";
import * as J from "../JavaIntf";
import { S } from "../Singletons";
import { Validator } from "../Validator";

export class EditBlockedWordsDlg extends DialogBase {
    wordsState: Validator = new Validator();
    textScrollPos = new ScrollPos();

    constructor() {
        super("Blocked Words", "app-modal-content-medium-width");
    }

    renderDlg(): CompIntf[] {
        return [
            new Div(null, null, [
                new Div("Content containing these will be filtered from your feed."),
                new TextArea("Blocked Words", { rows: 15 }, this.wordsState, null, false, 3, this.textScrollPos),
                new ButtonBar([
                    new Button("Save", this.save, null, "btn-primary"),
                    new Button("Close", this.close, null, "btn-secondary float-end")
                ], "marginTop")
            ])
        ];
    }

    reload = async () => {
        const res = await S.rpcUtil.rpc<J.GetUserProfileRequest, J.GetUserProfileResponse>("getUserProfile", {
            userId: getAs().userProfile.userNodeId
        });

        if (res?.userProfile) {
            this.wordsState.setValue(res.userProfile.blockedWords);
        }
    }

    save = () => {
        const ast = getAs();
        ast.userProfile.blockedWords = this.wordsState.getValue();

        dispatch("SetUserProfile", s => {
            s.userProfile = ast.userProfile;
        });

        S.rpcUtil.rpc<J.SaveUserProfileRequest, J.SaveUserProfileResponse>("saveUserProfile", {
            userName: null,
            userTags: ast.userProfile.userTags,
            blockedWords: ast.userProfile.blockedWords,
            userBio: ast.userProfile.userBio,
            displayName: ast.userProfile.displayName,
            publish: false,
            mfsEnable: ast.userProfile.mfsEnable
        });
        this.close();
    }

    async preLoad(): Promise<void> {
        await this.reload();
    }
}
